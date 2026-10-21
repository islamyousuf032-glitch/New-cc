package com.owncut.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Lossless stream-copy export for a deliberately narrow, verifiable subset of the editor timeline.
 * No frame rendering or re-encoding is performed: clips must be non-overlapping, start on video sync
 * samples, and share compatible codec formats. Explicitly accepted empty gaps are concatenated.
 */
internal object TimelineRemuxExporter {
    private const val FRAME_RATE = 30L
    private const val FRAME_ALIGNMENT_TOLERANCE_US = 1_000_000L / (FRAME_RATE * 2L) + 1_000L
    private const val KEYFRAME_TOLERANCE_US = 1_000L
    private const val MAX_SAMPLE_BUFFER_BYTES = 32 * 1024 * 1024

    data class Plan(
        val clips: List<ClipSnapshot>,
        val trackId: String,
        val omittedGapFrames: Long,
    )

    fun makePlan(clips: List<ClipSnapshot>): Plan {
        require(clips.isNotEmpty()) { "Timeline-এ export করার মতো clip নেই" }
        val ordered = clips.sortedBy { it.startFrame }
        val track = ordered.first().trackId
        require(ordered.all { it.mediaId.isNotBlank() }) {
            "প্রতিটি clip-এর জন্য original media reference দরকার"
        }
        require(ordered.all { it.trackId == track }) {
            "এই export সংস্করণে একটি video track সমর্থিত"
        }
        var previousTimelineEnd = 0L
        var outputFrame = 0L
        var omittedGapFrames = 0L
        val normalized = ArrayList<ClipSnapshot>(ordered.size)
        for (clip in ordered) {
            require(clip.startFrame >= previousTimelineEnd) {
                "Timeline clip-গুলো overlap করছে; overlap সরিয়ে আবার export করুন"
            }
            omittedGapFrames = Math.addExact(omittedGapFrames, clip.startFrame - previousTimelineEnd)
            require(clip.durationFrames > 0L && clip.sourceOutMs > clip.sourceInMs) {
                "Timeline clip-এর source range বৈধ নয়"
            }
            val sourceDurationUs = Math.multiplyExact(clip.sourceOutMs - clip.sourceInMs, 1_000L)
            val timelineDurationUs = Math.multiplyExact(clip.durationFrames, 1_000_000L) / FRAME_RATE
            require(abs(sourceDurationUs - timelineDurationUs) <= FRAME_ALIGNMENT_TOLERANCE_US) {
                "Clip-এর source duration timeline-এর কাছাকাছি frame boundary-তে round হয় না; trim range যাচাই করুন"
            }
            normalized += clip.copy(startFrame = outputFrame)
            outputFrame = Math.addExact(outputFrame, clip.durationFrames)
            previousTimelineEnd = Math.addExact(clip.startFrame, clip.durationFrames)
        }
        return Plan(normalized, track, omittedGapFrames)
    }

    fun export(
        context: Context,
        plan: Plan,
        output: File,
        cancelled: AtomicBoolean,
        progress: (String) -> Unit,
    ) {
        val firstMediaId = plan.clips.first().mediaId
        val probe = newExtractor(context, Uri.parse(firstMediaId))
        val tracksByMediaId = linkedMapOf<String, Tracks>()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var expectedAudioTrack = false
        try {
            val primaryTracks = findTracks(probe)
            require(primaryTracks.videoIndex >= 0) { "Original media-তে video track নেই" }
            tracksByMediaId[firstMediaId] = primaryTracks
            for (mediaId in plan.clips.asSequence().map { it.mediaId }.distinct().drop(1)) {
                ensureNotCancelled(cancelled)
                val sourceProbe = newExtractor(context, Uri.parse(mediaId))
                try {
                    val tracks = findTracks(sourceProbe)
                    require(tracks.videoIndex >= 0) { "Original media-তে video track নেই" }
                    requireCompatibleTracks(primaryTracks, tracks)
                    tracksByMediaId[mediaId] = tracks
                } finally {
                    sourceProbe.release()
                }
            }
            expectedAudioTrack = primaryTracks.audioIndex >= 0

            for (clip in plan.clips) {
                ensureNotCancelled(cancelled)
                verifySyncBoundary(
                    context,
                    Uri.parse(clip.mediaId),
                    tracksByMediaId.getValue(clip.mediaId).videoIndex,
                    clip.sourceInMs * 1000L,
                )
            }

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputVideo = muxer.addTrack(primaryTracks.videoFormat)
            val outputAudio = if (expectedAudioTrack) muxer.addTrack(primaryTracks.audioFormat!!) else -1
            val rotation = rotationHint(primaryTracks.videoFormat)
            if (rotation == 90 || rotation == 180 || rotation == 270) muxer.setOrientationHint(rotation)
            muxer.start()
            muxerStarted = true

            val maxInput = tracksByMediaId.values.flatMap { tracks ->
                listOfNotNull(
                    tracks.videoFormat.maxInputSizeOrNull(),
                    tracks.audioFormat?.maxInputSizeOrNull(),
                )
            }.maxOrNull()?.coerceIn(64 * 1024, MAX_SAMPLE_BUFFER_BYTES) ?: 1024 * 1024
            val sampleBuffer = ByteBuffer.allocateDirect(maxInput)
            val info = MediaCodec.BufferInfo()
            var lastVideoPtsUs = Long.MIN_VALUE
            var lastAudioPtsUs = Long.MIN_VALUE

            for ((index, clip) in plan.clips.withIndex()) {
                ensureNotCancelled(cancelled)
                progress("Encoded samples export হচ্ছে · ${index + 1}/${plan.clips.size}")
                val sourceTracks = tracksByMediaId.getValue(clip.mediaId)
                val extractor = newExtractor(context, Uri.parse(clip.mediaId))
                try {
                    extractor.selectTrack(sourceTracks.videoIndex)
                    if (sourceTracks.audioIndex >= 0) extractor.selectTrack(sourceTracks.audioIndex)
                    val sourceInUs = clip.sourceInMs * 1000L
                    val sourceOutUs = clip.sourceOutMs * 1000L
                    val timelineStartUs = Math.multiplyExact(clip.startFrame, 1_000_000L) / FRAME_RATE
                    val timelineDurationUs = Math.multiplyExact(clip.durationFrames, 1_000_000L) / FRAME_RATE
                    extractor.seekTo(sourceInUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    var firstVideoSampleSeen = false
                    while (extractor.sampleTrackIndex >= 0 && extractor.sampleTime < sourceOutUs) {
                        ensureNotCancelled(cancelled)
                        val sourceTrack = extractor.sampleTrackIndex
                        val sampleTime = extractor.sampleTime
                        val clipRelativeUs = sampleTime - sourceInUs
                        if (clipRelativeUs >= 0L && clipRelativeUs < timelineDurationUs &&
                            (sourceTrack == sourceTracks.videoIndex || sourceTrack == sourceTracks.audioIndex)) {
                            if (sourceTrack == sourceTracks.videoIndex && !firstVideoSampleSeen) {
                                val isSync = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                                require(isSync && abs(sampleTime - sourceInUs) <= KEYFRAME_TOLERANCE_US) {
                                    "Clip-এর শুরু sync frame-এ নেই; সঠিকভাবে export করতে keyframe-এ trim/split করুন"
                                }
                                firstVideoSampleSeen = true
                            }
                            sampleBuffer.clear()
                            val size = extractor.readSampleData(sampleBuffer, 0)
                            require(size > 0 && size <= sampleBuffer.capacity()) {
                                "Original media থেকে encoded sample পড়া যায়নি বা buffer সীমা ছাড়িয়েছে"
                            }
                            val outputPtsUs = Math.addExact(timelineStartUs, clipRelativeUs)
                            val destinationTrack = if (sourceTrack == sourceTracks.videoIndex) {
                                require(outputPtsUs > lastVideoPtsUs) {
                                    "Video sample timestamp order এই file-এ stream-copy export-এর উপযোগী নয়"
                                }
                                lastVideoPtsUs = outputPtsUs
                                outputVideo
                            } else {
                                require(outputPtsUs > lastAudioPtsUs) {
                                    "Audio sample timestamp order এই file-এ stream-copy export-এর উপযোগী নয়"
                                }
                                lastAudioPtsUs = outputPtsUs
                                outputAudio
                            }
                            info.set(0, size, outputPtsUs, toMuxerFlags(extractor.sampleFlags))
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
        try {
            progress("MP4 যাচাই হচ্ছে…")
            verifyMuxedOutput(output, expectedAudioTrack, cancelled)
            progress("MP4 stream তৈরি হয়েছে")
        } catch (error: Exception) {
            output.delete()
            throw error
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

    private fun requireCompatibleTracks(reference: Tracks, candidate: Tracks) {
        require((reference.audioIndex >= 0) == (candidate.audioIndex >= 0)) {
            "সব source clip-এ একই audio track layout থাকতে হবে"
        }
        requireCompatibleFormat(reference.videoFormat, candidate.videoFormat, "video")
        if (reference.audioIndex >= 0) {
            requireCompatibleFormat(reference.audioFormat!!, candidate.audioFormat!!, "audio")
        }
    }

    internal fun formatsCompatible(reference: MediaFormat, candidate: MediaFormat, kind: String): Boolean {
        if (reference.getString(MediaFormat.KEY_MIME) != candidate.getString(MediaFormat.KEY_MIME)) return false
        val keys = if (kind == "video") {
            listOf(
                MediaFormat.KEY_WIDTH,
                MediaFormat.KEY_HEIGHT,
                MediaFormat.KEY_PROFILE,
                MediaFormat.KEY_LEVEL,
                MediaFormat.KEY_COLOR_STANDARD,
                MediaFormat.KEY_COLOR_RANGE,
                MediaFormat.KEY_COLOR_TRANSFER,
            )
        } else {
            listOf(
                MediaFormat.KEY_SAMPLE_RATE,
                MediaFormat.KEY_CHANNEL_COUNT,
                MediaFormat.KEY_PROFILE,
                MediaFormat.KEY_LEVEL,
            )
        }
        for (key in keys) {
            val first = if (reference.containsKey(key)) reference.getInteger(key) else null
            val next = if (candidate.containsKey(key)) candidate.getInteger(key) else null
            if (first != next) return false
        }
        for (index in 0..3) {
            val key = "csd-$index"
            val first = reference.getByteBuffer(key)?.copyRemainingBytes()
            val next = candidate.getByteBuffer(key)?.copyRemainingBytes()
            if (!byteArraysEqual(first, next)) return false
        }
        return kind != "video" || rotationHint(reference) == rotationHint(candidate)
    }

    private fun requireCompatibleFormat(reference: MediaFormat, candidate: MediaFormat, kind: String) {
        require(formatsCompatible(reference, candidate, kind)) {
            "একাধিক source export করতে সব clip-এর $kind codec, dimensions ও codec configuration একই হতে হবে"
        }
    }

    private fun rotationHint(format: MediaFormat): Int =
        if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0

    private fun ByteBuffer.copyRemainingBytes(): ByteArray {
        val duplicate = duplicate()
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return bytes
    }

    private fun byteArraysEqual(first: ByteArray?, second: ByteArray?): Boolean = when {
        first == null -> second == null
        second == null -> false
        else -> first.contentEquals(second)
    }

    internal fun verifyMuxedOutput(
        output: File,
        expectedAudioTrack: Boolean,
        cancelled: AtomicBoolean,
        expectedDurationUs: Long? = null,
    ) {
        require(output.isFile && output.length() > 0L) { "Muxer কোনো বৈধ MP4 output তৈরি করেনি" }
        val extractor = MediaExtractor()
        var maximumVideoIntervalUs = 33_334L
        try {
            extractor.setDataSource(output.absolutePath)
            val tracks = findTracks(extractor)
            require(tracks.videoIndex >= 0) { "তৈরি MP4-তে video track খোলা যাচ্ছে না" }
            require((tracks.audioIndex >= 0) == expectedAudioTrack) {
                "তৈরি MP4-তে audio track অসম্পূর্ণ"
            }
            extractor.selectTrack(tracks.videoIndex)
            if (tracks.audioIndex >= 0) extractor.selectTrack(tracks.audioIndex)
            var videoSamples = 0
            var audioSamples = 0
            var lastVideoTime = Long.MIN_VALUE
            var lastAudioTime = Long.MIN_VALUE
            // Negative AAC pre-roll PTS is not EOF; track index is the unambiguous EOS signal.
            while (extractor.sampleTrackIndex >= 0) {
                ensureNotCancelled(cancelled)
                when (extractor.sampleTrackIndex) {
                    tracks.videoIndex -> {
                        require(extractor.sampleTime > lastVideoTime) { "তৈরি MP4-তে video timestamp সঠিক নয়" }
                        if (lastVideoTime != Long.MIN_VALUE)
                            maximumVideoIntervalUs = maxOf(maximumVideoIntervalUs, extractor.sampleTime - lastVideoTime)
                        lastVideoTime = extractor.sampleTime
                        videoSamples++
                    }
                    tracks.audioIndex -> {
                        require(extractor.sampleTime > lastAudioTime) { "তৈরি MP4-তে audio timestamp সঠিক নয়" }
                        lastAudioTime = extractor.sampleTime
                        audioSamples++
                    }
                }
                if (!extractor.advance()) break
            }
            require(videoSamples > 0) { "তৈরি MP4-তে video sample নেই" }
            require(!expectedAudioTrack || audioSamples > 0) { "তৈরি MP4-তে audio sample নেই" }
        } finally {
            extractor.release()
        }

        ensureNotCancelled(cancelled)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(output.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: throw IllegalArgumentException("তৈরি MP4-র duration পড়া যাচ্ছে না")
            require(durationMs > 0L) { "তৈরি MP4-র duration শূন্য" }
            expectedDurationUs?.let { expected ->
                val tolerance = maxOf(250_000L, maximumVideoIntervalUs * 2L)
                require(abs(durationMs * 1000L - expected) <= tolerance) {
                    "Render duration timeline-এর সঙ্গে মিলছে না; অসম্পূর্ণ output সেভ হয়নি"
                }
            }
            for (timeUs in listOf(0L, ((durationMs - 50L).coerceAtLeast(0L)) * 1000L).distinct()) {
                ensureNotCancelled(cancelled)
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: throw IllegalArgumentException("তৈরি MP4-র শুরু/শেষ video frame Android decoder খুলতে পারেনি")
                try { ensureNotCancelled(cancelled) } finally { frame.recycle() }
            }
        } finally {
            retriever.release()
        }
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
