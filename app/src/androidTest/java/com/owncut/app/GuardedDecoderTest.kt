package com.owncut.app

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Codec
import androidx.media3.transformer.ExportException
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference

/** Synthetic Codec delegation only; added, Android compilation/execution NOT verified.
 * No actual MediaCodec, rendered surface, source identity or encoded-pixel proof.
 */
@UnstableApi
class GuardedDecoderTest {
    private val color=ColorEngine.VideoColorMetadata(ColorEngine.VideoPrimaries.REC709,
        ColorEngine.VideoTransfer.SDR_VIDEO,ColorEngine.VideoRange.LIMITED)
    private val expected=ColorEngine.DecodedVideoFormat(1920,1080,color)
    private fun format()=Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(1920).setHeight(1080)
        .setColorInfo(ColorInfo.Builder().setColorSpace(C.COLOR_SPACE_BT709).setColorTransfer(C.COLOR_TRANSFER_SDR)
            .setColorRange(C.COLOR_RANGE_LIMITED).build()).build()
    private class Fake {
        var output: Format?=null
        var queryFails=false
        var releaseFails=false
        var closes=0
        val renders=mutableListOf<Any>()
        val codec=Proxy.newProxyInstance(Codec::class.java.classLoader,arrayOf(Codec::class.java)) { proxy,method,args ->
            when(method.name) {
                "getOutputFormat" -> { check(!queryFails) { "synthetic output query failure" };output }
                "releaseOutputBuffer" -> { check(!releaseFails) { "synthetic release failure" };renders.add(args!![0]);null }
                "release" -> { closes++;null }
                "toString" -> "Synthetic decoder"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> error("Unexpected synthetic Codec call: ${method.name}")
            }
        } as Codec
    }
    @Test fun bothRenderedOverloadsValidateEveryTimeAndFailureIsTerminal() {
        val fake=Fake()
        val codec=GuardedDecoderFactory.GuardedCodec(fake.codec,expected)
        codec.releaseOutputBuffer(false) // dropped before output format is known
        fake.output=format()
        codec.releaseOutputBuffer(true)
        codec.releaseOutputBuffer(123456L)
        assertEquals(listOf(false,true,123456L),fake.renders)
        fake.output=format().buildUpon().setWidth(1280).build()
        assertThrows(ExportException::class.java) { codec.releaseOutputBuffer(true) }
        fake.output=format()
        assertThrows(ExportException::class.java) { codec.releaseOutputBuffer(999L) }
        assertEquals(3,fake.renders.size)
        codec.releaseOutputBuffer(false) // cleanup drop still allowed
        codec.release();codec.release();assertEquals(1,fake.closes)
        assertThrows(IllegalStateException::class.java) { codec.releaseOutputBuffer(true) }
    }
    @Test fun absentFormatAndFailedQueryNeverRenderOrResume() {
        for (queryFails in listOf(false,true)) {
            val fake=Fake().apply { this.queryFails=queryFails }
            val codec=GuardedDecoderFactory.GuardedCodec(fake.codec,expected)
            try {
                assertThrows(ExportException::class.java) { codec.releaseOutputBuffer(true) }
                fake.queryFails=false;fake.output=format()
                assertThrows(ExportException::class.java) { codec.releaseOutputBuffer(true) }
                assertTrue(fake.renders.isEmpty())
            } finally { codec.release() }
        }
    }
    @Test fun delegateFailureAndWrongThreadCannotBypassGate() {
        val fake=Fake().apply { output=format();releaseFails=true }
        val codec=GuardedDecoderFactory.GuardedCodec(fake.codec,expected)
        try {
            assertThrows(IllegalStateException::class.java) { codec.releaseOutputBuffer(true) }
            fake.releaseFails=false
            assertThrows(ExportException::class.java) { codec.releaseOutputBuffer(true) }
            val failure=AtomicReference<Throwable?>()
            Thread { try { codec.releaseOutputBuffer(false) } catch(error: Throwable) { failure.set(error) } }
                .apply { start();join() }
            assertTrue(failure.get() is IllegalStateException)
            assertTrue(fake.renders.isEmpty())
        } finally { codec.release() }
    }
}
