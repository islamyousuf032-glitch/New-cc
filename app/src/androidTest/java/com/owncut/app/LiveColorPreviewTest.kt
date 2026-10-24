package com.owncut.app

import android.media.MediaFormat
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.*
import org.junit.Test

/** Added source, UNCOMPILED/UNRUN on Android. This is raw format transport, not live
 * decoder/graph playback proof. Actual clip-loop/seek/audio/surface checks remain open.
 */
@UnstableApi
class LiveColorPreviewTest {
    @Test fun rendererRawOutputNeverDefaultsMissingColorTags() {
        val format=MediaFormat.createVideoFormat("video/raw",1920,1080)
        val unknown=VideoSourceProbe.decoded(format)
        assertEquals(ColorEngine.VideoPrimaries.UNKNOWN,unknown.color.primaries)
        assertThrows(IllegalArgumentException::class.java) { ColorEngine.validateDecodedFormat(unknown,unknown) }
        format.setInteger(MediaFormat.KEY_COLOR_STANDARD,MediaFormat.COLOR_STANDARD_BT709)
        format.setInteger(MediaFormat.KEY_COLOR_TRANSFER,MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
        format.setInteger(MediaFormat.KEY_COLOR_RANGE,MediaFormat.COLOR_RANGE_LIMITED)
        val known=VideoSourceProbe.decoded(format)
        ColorEngine.validateDecodedFormat(known,known)
        val gate=DecoderColorGate(known)
        gate.beforeRender(known)
        format.setInteger(MediaFormat.KEY_COLOR_TRANSFER,MediaFormat.COLOR_TRANSFER_HLG)
        assertThrows(IllegalArgumentException::class.java) { gate.beforeRender(VideoSourceProbe.decoded(format)) }
        assertEquals(DecoderColorGate.State.FAILED,gate.state)
        assertThrows(IllegalStateException::class.java) { gate.beforeRender(known) }
    }
}
