package com.owncut.app

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Codec
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Android codec adapter. Timeline edits remain owned by the native engine. */
@UnstableApi
internal object TimelineRenderExporter {
    // A conservative first output preset for mid-range phones. Never silently fall back to remux.
    const val OUTPUT_HEIGHT = 720

    internal fun composition(plan: TimelineRemuxExporter.Plan): Composition {
        val items = plan.clips.map { clip ->
            val media = MediaItem.Builder()
                .setUri(clip.mediaId)
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.sourceInMs)
                    .setEndPositionMs(clip.sourceOutMs)
                    .build())
                .build()
            EditedMediaItem.Builder(media).build()
        }
        val sequence = EditedMediaItemSequence.Builder(items)
            .experimentalSetForceAudioTrack(true)
            .build()
        return Composition.Builder(sequence)
            .setTransmuxAudio(false)
            .setTransmuxVideo(false)
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(OUTPUT_HEIGHT))))
            .build()
    }

    /** Blocking worker entry point; Transformer and all its operations stay on one owned looper. */
    fun export(
        context: Context,
        plan: TimelineRemuxExporter.Plan,
        output: File,
        cancelled: AtomicBoolean,
        progress: (String) -> Unit,
    ) {
        check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper())
        val composition = composition(plan)
        val thread = HandlerThread("OwncutRender").apply { start() }
        val handler = Handler(thread.looper)
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        var transformer: Transformer? = null // accessed only on the render looper
        val ticker = object : Runnable {
            override fun run() {
                if (finished.count == 0L) return
                try {
                    if (cancelled.get()) {
                        transformer?.cancel()
                        failure.set(CancellationException("Render বাতিল করা হয়েছে"))
                        finished.countDown()
                        return
                    }
                    val holder = ProgressHolder()
                    val state = transformer?.getProgress(holder)
                    progress(if (state == Transformer.PROGRESS_STATE_AVAILABLE)
                        "ভিডিও render হচ্ছে · ${holder.progress}%" else "ভিডিও render হচ্ছে…")
                    handler.postDelayed(this, 250L)
                } catch (error: Throwable) {
                    failure.set(error)
                    finished.countDown()
                }
            }
        }
        handler.post {
            try {
                if (cancelled.get()) throw CancellationException("Render বাতিল করা হয়েছে")
                val platform = DefaultEncoderFactory.Builder(context.applicationContext)
                    .setEnableFallback(true).build()
                val alwaysEncode = object : Codec.EncoderFactory by platform {
                    override fun videoNeedsEncoding() = true
                    override fun audioNeedsEncoding() = true
                }
                transformer = Transformer.Builder(context.applicationContext)
                    .setLooper(thread.looper)
                    .setEncoderFactory(alwaysEncode)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            finished.countDown()
                        }
                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            failure.set(exportException)
                            finished.countDown()
                        }
                    }).build()
                transformer!!.start(composition, output.absolutePath)
                handler.post(ticker)
            } catch (error: Throwable) {
                failure.set(error)
                finished.countDown()
            }
        }
        var succeeded = false
        try {
            // Codec hangs must not leave an indefinitely busy editor. Device runs still need validation.
            check(finished.await(30, TimeUnit.MINUTES)) { "Render সময়সীমা শেষ; আবার চেষ্টা করুন" }
            failure.get()?.let { throw it }
            if (cancelled.get()) throw CancellationException("Render বাতিল করা হয়েছে")
            progress("Render সম্পন্ন · MP4 যাচাই হচ্ছে…")
            val expectedDurationUs = plan.clips.fold(0L) { total, clip ->
                Math.addExact(total, Math.multiplyExact(clip.sourceOutMs - clip.sourceInMs, 1000L))
            }
            TimelineRemuxExporter.verifyMuxedOutput(output, true, cancelled, expectedDurationUs)
            succeeded = true
        } catch (interrupted: InterruptedException) {
            cancelled.set(true)
            throw CancellationException("Render বন্ধ করা হয়েছে").apply { initCause(interrupted) }
        } finally {
            val stopped = CountDownLatch(1)
            handler.removeCallbacks(ticker)
            handler.post {
                try { transformer?.cancel() } finally { stopped.countDown() }
            }
            // Never delete a file while the codec still has an open muxer.
            var interrupted = false
            while (true) {
                try { stopped.await(); break } catch (_: InterruptedException) { interrupted = true }
            }
            thread.quitSafely()
            while (thread.isAlive) {
                try { thread.join() } catch (_: InterruptedException) { interrupted = true }
            }
            if (interrupted) Thread.currentThread().interrupt()
            if (!succeeded) output.delete()
        }
    }
}
