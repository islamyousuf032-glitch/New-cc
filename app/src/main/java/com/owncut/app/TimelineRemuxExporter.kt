package com.owncut.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Lossless stream-copy export for a deliberately narrow, verifiable subset of the editor timeline.
 * No frame rendering or re-encoding is performed: clips must be contiguous, share one source, and
 * begin on exact video sync samples. Unsupported edits fail instead of producing misleading output.
 */
internal object TimelineRemuxExporter {
    private const val FRAME_RATE = 30L
    private const val KEYFRAME_TOLERANCE_US = 1_000L
    private const val MAX_SAMPLE_BUFFER_BYTES = 32 * 1024 * 1024

    data class Plan(val clips: List<ClipSnapshot>, val mediaId: String, val trackId: String)

    fun makePlan(clips: List<ClipSnapshot>): Plan {
        require(clips.isNotEmpty()) { "Timeline-এ export করার মতো clip নেই" }
        val ordered = clips.sortedBy { it.startFrame }
        val source = ordered.first().mediaId
        val track = ordered.first().trackId
        require(ordered.all { it.mediaId == source }) {
            "এই export সংস্করণে সব clip একই original video থেকে হতে হবে"
        }
        require(ordered.all { it.trackId == track }) {
            "এই export সংস্করণে একটি video track সমর্থিত"
        }
        var nextFrame = 0L
        for (clip in ordered) {
            require(clip.startFrame == nextFrame) {
                "Timeline-এ gap/overlap আছে; এই export সংস্করণ কেবল পরপর clip export করে"
            }
            require(clip.durationFrames > 0L && clip.sourceOutMs > clip.sourceInMs) {
                "Timeline clip-এর source range বৈধ নয়"
            }
            val sourceDurationUs = (clip.sourceOutMs - clip.sourceInMs) * 1_000L
            val timelineDurationUs = clip.durationFrames * 1_000_000L / FRAME_RATE
            require(abs(sourceDurationUs - timelineDurationUs) <= 1_000L) {
                "Clip-এর source/timeline duration sub-frame precision-এ মেলে না; এই lossless export নিরাপদ নয়"
            }
            nextFrame = Math.addExact(nextFrame, clip.durationFrames)
        }
        return Plan(ordered, source, track)
    }

    fun export(
        context: Context,
        plan: Plan,
        output: File,
        cancelled: AtomicBoolean,
        progress: (String) -> Unit,
    ) {
        val sourceUri = Uri.parse(plan.mediaId)
        val probe = newExtractor(context, sourceUri)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            val tracks = findTracks(probe)
            require(tracks.videoIndex >= 0) { "Original media-তে video track নেই" }
            require(tracks.videoFormat.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                "Original video format export-এর উপযোগী নয়"
            }
            for (clip in plan.clips) {
                ensureNotCancelled(cancelled)
                verifySyncBoundary(context, sourceUri, tracks.videoIndex, clip.sourceInMs * 1000L)
            }

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputVideo = muxer.addTrack(tracks.videoFormat)
            val outputAudio = if (tracks.audioIndex >= 0) muxer.addTrack(tracks.audioFormat!!) else -1
            if (tracks.videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                val rotation = tracks.videoFormat.getInteger(MediaFormat.KEY_ROTATION)
                if (rotation == 90 || rotation == 180 || rotation == 270) muxer.setOrientationHint(rotation)
            }
            muxer.start()
            muxerStarted = true

            val maxInput = listOfNotNull(
                tracks.videoFormat.maxInputSizeOrNull(),
                tracks.audioFormat?.maxInputSizeOrNull(),
            ).maxOrNull()?.coerceIn(64 * 1024, MAX_SAMPLE_BUFFER_BYTES) ?: 1024 * 1024
            val sampleBuffer = ByteBuffer.allocateDirect(maxInput)
            val info = MediaCodec.BufferInfo()
            var lastVideoPtsUs = Long.MIN_VALUE
            var lastAudioPtsUs = Long.MIN_VALUE

            for ((index, clip) in plan.clips.withIndex()) {
                ensureNotCancelled(cancelled)
                progress("Encoded samples export হচ্ছে · ${index + 1}/${plan.clips.size}")
                val extractor = newExtractor(context, sourceUri)
                try {
                    extractor.selectTrack(tracks.videoIndex)
                    if (tracks.audioIndex >= 0) extractor.selectTrack(tracks.audioIndex)
                    val sourceInUs = clip.sourceInMs * 1000L
                    val sourceOutUs = clip.sourceOutMs * 1000L
                    val timelineStartUs = clip.startFrame * 1_000_000L / FRAME_RATE
                    extractor.seekTo(sourceInUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    var firstVideoSampleSeen = false
                    while (extractor.sampleTime >= 0L && extractor.sampleTime < sourceOutUs) {
                        ensureNotCancelled(cancelled)
                        val sourceTrack = extractor.sampleTrackIndex
                        val sampleTime = extractor.sampleTime
                        if (sampleTime >= sourceInUs && (sourceTrack == tracks.videoIndex || sourceTrack == tracks.audioIndex)) {
                            if (sourceTrack == tracks.videoIndex && !firstVideoSampleSeen) {
                                val isSync = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                                require(isSync && abs(sampleTime - sourceInUs) <= KEYFRAME_TOLERANCE_US) {
                                    "Clip-এর শুরু sync frame-এ নেই; সঠিকভাবে export করতে keyframe-এ trim/split করুন"
                                }
                                firstVideoSampleSeen = true
                            }
                            sampleBuffer.clear()
                            val size = extractor.readSampleData(sampleBuffer, 0)
                            require(size > 0) { "Original media থেকে encoded sample পড়া যায়নি বা buffer সীমা ছাড়িয়েছে" }
                            val outputPtsUs = timelineStartUs + (sampleTime - sourceInUs)
                            if (sourceTrack == tracks.videoIndex) {
                                require(outputPtsUs > lastVideoPtsUs) {
                                    "Video sample timestamp order এই file-এ stream-copy export-এর উপযোগী নয়"
                                }
                                lastVideoPtsUs = outputPtsUs
                            } else {
                                require(outputPtsUs > lastAudioPtsUs) {
                                    "Audio sample timestamp order এই file-এ stream-copy export-এর উপযোগী নয়"
                                }
                                lastAudioPtsUs = outputPtsUs
                            }
                            info.set(0, size, outputPtsUs, toMuxerFlags(extractor.sampleFlags))
                            val destinationTrack = if (sourceTrack == tracks.videoIndex) outputVideo else outputAudio
                            muxer.writeSampleData(destinationTrack, sampleBuffer, info)
                        }
                        if (!extractor.advance()) break
                    }
                    require(firstVideoSampleSeen) { "Clip source range-এ video sample পাওয়া যায়নি" }
                } finally {
                    extractor.release()
                }
            }
            ensureNotCancelled(cancelled)
            muxer.stop()
            muxerStarted = false
            progress("MP4 stream তৈরি হয়েছে")
        } catch (cancel: CancellationException) {
            output.delete()
            throw cancel
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            if (muxerStarted) {
                try { muxer?.stop() } catch (_: RuntimeException) { }
            }
            muxer?.release()
            probe.release()
        }
    }

    private data class Tracks(
        val videoIndex: Int,
        val videoFormat: MediaFormat,
        val audioIndex: Int,
        val audioFormat: MediaFormat?,
    )

    private fun findTracks(extractor: MediaExtractor): Tracks {
        var videoIndex = -1
        var videoFormat: MediaFormat? = null
        var audioIndex = -1
        var audioFormat: MediaFormat? = null
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            when {
                mime.startsWith("video/") -> {
                    require(videoIndex < 0) { "একাধিক video track এই export সংস্করণে সমর্থিত নয়" }
                    videoIndex = index
                    videoFormat = format
                }
                mime.startsWith("audio/") -> {
                    require(audioIndex < 0) { "একাধিক audio track এই export সংস্করণে সমর্থিত নয়" }
                    audioIndex = index
                    audioFormat = format
                }
            }
        }
        return Tracks(
            videoIndex,
            videoFormat ?: MediaFormat(),
            audioIndex,
            audioFormat,
        )
    }

    private fun verifySyncBoundary(context: Context, uri: Uri, videoTrack: Int, sourceInUs: Long) {
        val extractor = newExtractor(context, uri)
        try {
            extractor.selectTrack(videoTrack)
            extractor.seekTo(sourceInUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            while (extractor.sampleTime >= 0L) {
                val time = extractor.sampleTime
                if (time >= sourceInUs && extractor.sampleTrackIndex == videoTrack &&
                    extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    require(abs(time - sourceInUs) <= KEYFRAME_TOLERANCE_US) {
                        "Clip-এর শুরু keyframe-এ নেই; সঠিক export-এর জন্য keyframe-এ split/trim করুন"
                    }
                    return
                }
                if (!extractor.advance()) break
            }
            throw IllegalArgumentException("Clip-এর শুরুতে নির্ভরযোগ্য video keyframe পাওয়া যায়নি")
        } finally {
            extractor.release()
        }
    }

    private fun toMuxerFlags(sampleFlags: Int): Int {
        require(sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
            "DRM/encrypted sample stream-copy-তে সমর্থিত নয়"
        }
        require(sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME == 0) {
            "Fragmented sample stream-copy-তে সমর্থিত নয়"
        }
        return if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else {
            0
        }
    }

    private fun newExtractor(context: Context, uri: Uri): MediaExtractor {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            return extractor
        } catch (error: Exception) {
            extractor.release()
            throw error
        }
    }

    private fun MediaFormat.maxInputSizeOrNull(): Int? =
        if (containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else null

    private fun ensureNotCancelled(cancelled: AtomicBoolean) {
        if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException("Export বাতিল করা হয়েছে")
    }
}
