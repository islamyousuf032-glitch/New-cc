package com.owncut.app

import org.json.JSONObject
import org.json.JSONArray

internal data class MediaAssetSummary(val mediaId: String, val clipIds: List<String>, val requiredSourceEndMs: Long)

internal data class PlayheadPosition(val clipId: String, val timelineFrame: Long, val sourcePositionMs: Long)

/**
 * Owns one native engine session. Native work is intentionally batched at this boundary.
 * Project and autosave file methods are blocking; call them from a worker, not the UI thread.
 */
internal class EngineSession : AutoCloseable {
    private var handle: Long = nativeCreate(java.util.UUID.randomUUID().toString())

    private val snapshotCache = TimelineSnapshotCache()

    init {
        check(handle != 0L) { AppLanguage.text(R.string.message_22b4d27a73) }
    }

    @Synchronized
    fun status(): String {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        return nativeGetStatus(handle)
    }

    @Synchronized
    fun clips(): List<ClipSnapshot> {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        return snapshotCache.read(nativeGetRevision(handle)) { offset, limit, revision ->
            clipPage(offset, limit, revision)
        }
    }

    @Synchronized
    fun clipPage(offset: Int, limit: Int = 256, expectedRevision: String? = null): ClipSnapshotPage {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        require(offset >= 0 && limit in 1..256)
        val json = JSONObject(nativeGetClipPage(handle, offset, limit, expectedRevision))
        return ClipSnapshotPage(parseClips(json.getJSONArray("clips")), json.getString("revision"),
            json.getInt("total"), json.getInt("nextOffset"))
    }

    private fun parseClips(array: JSONArray): List<ClipSnapshot> = List(array.length()) { index ->
        val clip = array.getJSONObject(index)
        val grade = clip.getJSONObject("grade")
        val values = grade.getJSONArray("parameters")
        ClipSnapshot(clip.getString("id"), clip.getString("trackId"), clip.getString("mediaId"),
            clip.getLong("startFrame"), clip.getLong("durationFrames"),
            clip.getLong("sourceInMs"), clip.getLong("sourceOutMs"),
            ColorGrade.fromParametersV1(DoubleArray(values.length()) { values.getDouble(it) },grade.getBoolean("enabled")),
            clip.getBoolean("gradeActive"))
    }
    private external fun nativeGetRevision(handle: Long): String
    private external fun nativeGetClipPage(handle: Long, offset: Int, limit: Int, expectedRevision: String?): String

    @Synchronized
    fun setClipGrades(clipIds: List<String>, grade: ColorGrade) {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        nativeSetClipGrades(handle,clipIds.toTypedArray(),grade.parametersV1(),grade.enabled)
    }
    @Synchronized
    fun resetClipGrades(clipIds: List<String>) = setClipGrades(clipIds,ColorGrade())
    @Synchronized
    fun setClipGradeEnabled(clipId: String, enabled: Boolean) {
        val grade = clips().firstOrNull { it.id == clipId }?.grade
            ?: error(AppLanguage.text(R.string.message_3d1d8c290a))
        setClipGrades(listOf(clipId),grade.copy(enabled=enabled))
    }
    @Synchronized
    fun copyClipGrade(sourceId: String, targetIds: List<String>) {
        val grade = clips().firstOrNull { it.id == sourceId }?.grade
            ?: error(AppLanguage.text(R.string.message_3d1d8c290a))
        setClipGrades(targetIds,grade)
    }
    private external fun nativeSetClipGrades(handle: Long, targets: Array<String>, parameters: DoubleArray, enabled: Boolean)

    @Synchronized
    fun mediaAssets(): List<MediaAssetSummary> {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        val array = JSONObject(nativeGetMediaAssets(handle)).getJSONArray("assets")
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val ids = item.getJSONArray("clipIds")
            MediaAssetSummary(item.getString("mediaId"), List(ids.length()) { ids.getString(it) }, item.getLong("requiredSourceEndMs"))
        }
    }
    private external fun nativeGetMediaAssets(handle: Long): String

    @Synchronized
    fun resolvePlayhead(trackId: String, timelineFrame: Long): PlayheadPosition? {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        val json = nativeResolvePlayhead(handle, trackId, timelineFrame) ?: return null
        val value = JSONObject(json)
        return PlayheadPosition(value.getString("clipId"), value.getLong("timelineFrame"), value.getLong("sourcePositionMs"))
    }

    @Synchronized
    fun addMediaClip(mediaId: String, durationMs: Long): String {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        return nativeAddMediaClip(handle, mediaId, durationMs)
    }

    @Synchronized
    fun relinkClipMedia(clipId: String, replacementMediaId: String, replacementDurationMs: Long) {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        val all = clips()
        val target = all.firstOrNull { it.id == clipId } ?: error(AppLanguage.text(R.string.message_3d1d8c290a))
        val required = all.filter { it.mediaId == target.mediaId }.maxOf { it.sourceOutMs }
        require(replacementDurationMs >= required) {
            AppLanguage.text(R.string.message_02a0ab04a0, required)
        }
        nativeRelinkClipMedia(handle, clipId, replacementMediaId)
    }

    @Synchronized
    fun moveClip(clipId: String, destinationTrackId: String, destinationStartFrame: Long) {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        nativeMoveClip(handle, clipId, destinationTrackId, destinationStartFrame)
    }

    @Synchronized
    fun splitClip(clipId: String, cutFrame: Long, rightClipId: String) {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        nativeSplitClip(handle, clipId, cutFrame, rightClipId)
    }

    @Synchronized
    fun trimClip(clipId: String, leftEdge: Boolean, sourceBoundaryMs: Long) {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        nativeTrimClip(handle, clipId, leftEdge, sourceBoundaryMs)
    }

    @Synchronized
    fun deleteClip(clipId: String, ripple: Boolean = false) {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        nativeDeleteClip(handle, clipId, ripple)
    }

    @Synchronized
    fun undo(): Boolean {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        return nativeUndo(handle)
    }

    @Synchronized
    fun redo(): Boolean {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        return nativeRedo(handle)
    }

    @Synchronized
    fun saveProject(path: String) {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        nativeSaveProject(handle, path)
    }

    @Synchronized
    fun loadProject(path: String) {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        nativeLoadProject(handle, path)
    }

    @Synchronized
    fun saveAutosave(path: String) {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        nativeSaveAutosave(handle, path)
    }

    @Synchronized
    fun loadAutosave(path: String): Boolean {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        return nativeLoadAutosave(handle, path)
    }

    @Synchronized
    fun clearAutosave(path: String) {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        nativeClearAutosave(handle, path)
    }

    @Synchronized
    override fun close() {
        val current = handle
        if (current != 0L) {
            handle = 0L
            snapshotCache.clear()
            nativeDestroy(current)
        }
    }

    @Synchronized
    fun saveCopy(path: String) {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        nativeSaveCopy(handle,path)
    }
    @Synchronized
    fun restoreDraft(path: String) {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        nativeRestoreDraft(handle,path)
    }
    private external fun nativeSaveCopy(handle: Long, path: String)
    private external fun nativeRestoreDraft(handle: Long, path: String)
    @Synchronized
    fun checkpointProject(path: String) {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        nativeCheckpointProject(handle,path)
    }
    @Synchronized
    fun repairCheckpoint(path: String): Boolean {
        check(handle != 0L) { AppLanguage.text(R.string.message_bf5f0d21b6) }
        return nativeRepairCheckpoint(handle,path)
    }
    private external fun nativeRepairCheckpoint(handle: Long, path: String): Boolean
    private external fun nativeCheckpointProject(handle: Long, path: String)
    private external fun nativeCreate(projectId: String): Long
    private external fun nativeGetStatus(handle: Long): String
    private external fun nativeGetTimelineSnapshot(handle: Long): String
    private external fun nativeResolvePlayhead(handle: Long, trackId: String, timelineFrame: Long): String?
    private external fun nativeAddMediaClip(handle: Long, mediaId: String, durationMs: Long): String
    private external fun nativeRelinkClipMedia(handle: Long, clipId: String, replacementMediaId: String)
    private external fun nativeMoveClip(handle: Long, clipId: String, destinationTrackId: String, destinationStartFrame: Long)
    private external fun nativeSplitClip(handle: Long, clipId: String, cutFrame: Long, rightClipId: String)
    private external fun nativeTrimClip(handle: Long, clipId: String, leftEdge: Boolean, sourceBoundaryMs: Long)
    private external fun nativeDeleteClip(handle: Long, clipId: String, ripple: Boolean)
    private external fun nativeUndo(handle: Long): Boolean
    private external fun nativeRedo(handle: Long): Boolean
    private external fun nativeSaveProject(handle: Long, path: String)
    private external fun nativeLoadProject(handle: Long, path: String)
    private external fun nativeSaveAutosave(handle: Long, path: String)
    private external fun nativeLoadAutosave(handle: Long, path: String): Boolean
    private external fun nativeClearAutosave(handle: Long, path: String)
    private external fun nativeDestroy(handle: Long)

    companion object {
        init { System.loadLibrary("owncut_android") }
    }
}
