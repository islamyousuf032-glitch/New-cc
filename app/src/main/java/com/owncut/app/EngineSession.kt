package com.owncut.app

import org.json.JSONObject

internal data class ClipSnapshot(
    val id: String,
    val trackId: String,
    val mediaId: String,
    val startFrame: Long,
    val durationFrames: Long,
    val sourceInMs: Long,
    val sourceOutMs: Long,
)

/**
 * Owns one native engine session. Native work is intentionally batched at this boundary.
 * Project and autosave file methods are blocking; call them from a worker, not the UI thread.
 */
internal class EngineSession : AutoCloseable {
    private var handle: Long = nativeCreate()

    init {
        check(handle != 0L) { "Could not create native editor engine" }
    }

    @Synchronized
    fun status(): String {
        check(handle != 0L) { "Engine session is closed" }
        return nativeGetStatus(handle)
    }

    @Synchronized
    fun clips(): List<ClipSnapshot> {
        check(handle != 0L) { "Engine session is closed" }
        val array = JSONObject(nativeGetTimelineSnapshot(handle)).getJSONArray("clips")
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val clip = array.getJSONObject(index)
                add(ClipSnapshot(
                    id = clip.getString("id"),
                    trackId = clip.getString("trackId"),
                    mediaId = clip.getString("mediaId"),
                    startFrame = clip.getLong("startFrame"),
                    durationFrames = clip.getLong("durationFrames"),
                    sourceInMs = clip.getLong("sourceInMs"),
                    sourceOutMs = clip.getLong("sourceOutMs"),
                ))
            }
        }
    }

    @Synchronized
    fun addMediaClip(mediaId: String, durationMs: Long): String {
        check(handle != 0L) { "Engine session is closed" }
        return nativeAddMediaClip(handle, mediaId, durationMs)
    }

    @Synchronized
    fun moveClip(clipId: String, destinationTrackId: String, destinationStartFrame: Long) {
        check(handle != 0L) { "Engine session is closed" }
        nativeMoveClip(handle, clipId, destinationTrackId, destinationStartFrame)
    }

    @Synchronized
    fun splitClip(clipId: String, cutFrame: Long, rightClipId: String) {
        check(handle != 0L) { "Engine session is closed" }
        nativeSplitClip(handle, clipId, cutFrame, rightClipId)
    }

    @Synchronized
    fun trimClip(clipId: String, leftEdge: Boolean, sourceBoundaryMs: Long) {
        check(handle != 0L) { "Engine session is closed" }
        nativeTrimClip(handle, clipId, leftEdge, sourceBoundaryMs)
    }

    @Synchronized
    fun deleteClip(clipId: String, ripple: Boolean = false) {
        check(handle != 0L) { "Engine session is closed" }
        nativeDeleteClip(handle, clipId, ripple)
    }

    @Synchronized
    fun undo(): Boolean {
        check(handle != 0L) { "Engine session is closed" }
        return nativeUndo(handle)
    }

    @Synchronized
    fun redo(): Boolean {
        check(handle != 0L) { "Engine session is closed" }
        return nativeRedo(handle)
    }

    @Synchronized
    fun saveProject(path: String) {
        check(handle != 0L) { "Engine session is closed" }
        nativeSaveProject(handle, path)
    }

    @Synchronized
    fun loadProject(path: String) {
        check(handle != 0L) { "Engine session is closed" }
        nativeLoadProject(handle, path)
    }

    @Synchronized
    fun saveAutosave(path: String) {
        check(handle != 0L) { "Engine session is closed" }
        nativeSaveAutosave(handle, path)
    }

    @Synchronized
    fun loadAutosave(path: String): Boolean {
        check(handle != 0L) { "Engine session is closed" }
        return nativeLoadAutosave(handle, path)
    }

    @Synchronized
    fun clearAutosave(path: String) {
        check(handle != 0L) { "Engine session is closed" }
        nativeClearAutosave(handle, path)
    }

    @Synchronized
    override fun close() {
        val current = handle
        if (current != 0L) {
            handle = 0L
            nativeDestroy(current)
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeGetStatus(handle: Long): String
    private external fun nativeGetTimelineSnapshot(handle: Long): String
    private external fun nativeAddMediaClip(handle: Long, mediaId: String, durationMs: Long): String
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
