package com.owncut.app

import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SingleInputVideoGraph
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.PlaybackVideoGraphWrapper
import androidx.media3.exoplayer.video.VideoFrameReleaseControl
import androidx.media3.exoplayer.video.VideoRendererEventListener
import java.nio.ByteBuffer

/** Isolated opt-in player; does not replace the working timeline MediaPlayer lifecycle.
 * Renderer OWNS its video sink: base class creates/releases the graph through our factory.
 * Explicit same DEFAULT SDR texture pipeline as candidate export, never interface defaults.
 */
@UnstableApi
internal class GradedPreviewRenderers(context: Context, private val prepared: GradedFramePipeline.Prepared,
    private val source: VideoSourceMetadata) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(context: Context, extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector, enableDecoderFallback: Boolean, eventHandler: Handler,
        eventListener: VideoRendererEventListener, allowedVideoJoiningTimeMs: Long, out: ArrayList<Renderer>) {
        val builder=MediaCodecVideoRenderer.Builder(context).setMediaCodecSelector(mediaCodecSelector)
            .setEnableDecoderFallback(enableDecoderFallback).setEventHandler(eventHandler).setEventListener(eventListener)
            .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
        val expected=ColorEngine.DecodedVideoFormat(source.width,source.height,source.color)
        out.add(object : MediaCodecVideoRenderer(builder) {
            private val gate=DecoderColorGate(expected)
            override fun createPlaybackVideoGraphWrapper(context: Context,
                videoFrameReleaseControl: VideoFrameReleaseControl): PlaybackVideoGraphWrapper =
                PlaybackVideoGraphWrapper.Builder(context,videoFrameReleaseControl)
                    .setVideoGraphFactory(SingleInputVideoGraph.Factory(prepared.factory))
                    .setEnablePlaylistMode(true).setClock(getClock()).build()

            override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
                try {
                    // Reject changed/HDR input even if a platform decoder could tone-map its output.
                    ColorEngine.validateDecodedFormat(expected,GuardedDecoderFactory.descriptor(format))
                    gate.beforeRender(mediaFormat?.let { VideoSourceProbe.decoded(it) })
                } catch(error: Exception) {
                    gate.invalidate()
                    throw createRendererException(error,format,PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
                }
                super.onOutputFormatChanged(format,mediaFormat)
            }
            override fun processOutputBuffer(positionUs: Long, elapsedRealtimeUs: Long, codec: MediaCodecAdapter?,
                buffer: ByteBuffer?, bufferIndex: Int, bufferFlags: Int, sampleCount: Int,
                bufferPresentationTimeUs: Long, isDecodeOnlyBuffer: Boolean, isLastBuffer: Boolean, format: Format): Boolean {
                if(!isDecodeOnlyBuffer || isLastBuffer) {
                    try { gate.beforeRender { codec?.outputFormat?.let { VideoSourceProbe.decoded(it) } } }
                    catch(error: Exception) {
                        gate.invalidate()
                        throw createRendererException(error,format,PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
                    }
                }
                return super.processOutputBuffer(positionUs,elapsedRealtimeUs,codec,buffer,bufferIndex,bufferFlags,
                    sampleCount,bufferPresentationTimeUs,isDecodeOnlyBuffer,isLastBuffer,format)
            }
            override fun onRelease() {
                try { super.onRelease() } finally { gate.close() }
            }
        })
    }
}
