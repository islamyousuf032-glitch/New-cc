package com.owncut.app

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.util.UnstableApi

/** Platform metadata transport only. Missing tags remain UNKNOWN; never resolution defaults.
 * Worker-only, request-scoped extraction; ownership ends even on cancellation/failure.
 */
@UnstableApi
internal object VideoSourceProbe {
    fun read(context: Context, mediaId: String, checkCancelled: () -> Unit): VideoSourceMetadata {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Video metadata probing must run off the UI thread" }
        checkCancelled()
        val uri=Uri.parse(mediaId)
        require(uri.scheme in listOf("content","file")) { "Only local video sources are supported" }
        val extractor=MediaExtractor()
        try {
            extractor.setDataSource(context.applicationContext,uri,null)
            checkCancelled()
            var found: VideoSourceMetadata?=null
            for (index in 0 until extractor.trackCount) {
                checkCancelled()
                val format=extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") != true) continue
                require(found == null) { "Multiple video tracks require an explicit track selector" }
                fun integer(key: String): Int? = if(format.containsKey(key)) format.getInteger(key) else null
                found=VideoSourceMetadata(mediaId,index,integer(MediaFormat.KEY_WIDTH) ?: 0,
                    integer(MediaFormat.KEY_HEIGHT) ?: 0,integer(MediaFormat.KEY_ROTATION) ?: 0,
                    color(integer(MediaFormat.KEY_COLOR_STANDARD),integer(MediaFormat.KEY_COLOR_TRANSFER),
                        integer(MediaFormat.KEY_COLOR_RANGE)).copy(hasHdrStaticMetadata=
                            format.containsKey(MediaFormat.KEY_HDR_STATIC_INFO) &&
                                format.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO)?.let { it.limit() > 0 } == true))
            }
            checkCancelled()
            return requireNotNull(found) { "No video track in source" }
        } finally { extractor.release() }
    }

    // Android MediaFormat and Media3 constants are mapped independently, not assumed equal.
    internal fun color(standard: Int?, transfer: Int?, range: Int?)=ColorEngine.VideoColorMetadata(
        when(standard) { null -> ColorEngine.VideoPrimaries.UNKNOWN
            MediaFormat.COLOR_STANDARD_BT709 -> ColorEngine.VideoPrimaries.REC709
            else -> ColorEngine.VideoPrimaries.OTHER },
        when(transfer) { null -> ColorEngine.VideoTransfer.UNKNOWN
            MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> ColorEngine.VideoTransfer.SDR_VIDEO
            MediaFormat.COLOR_TRANSFER_ST2084 -> ColorEngine.VideoTransfer.PQ
            MediaFormat.COLOR_TRANSFER_HLG -> ColorEngine.VideoTransfer.HLG
            else -> ColorEngine.VideoTransfer.OTHER },
        when(range) { null -> ColorEngine.VideoRange.UNKNOWN
            MediaFormat.COLOR_RANGE_FULL -> ColorEngine.VideoRange.FULL
            MediaFormat.COLOR_RANGE_LIMITED -> ColorEngine.VideoRange.LIMITED
            else -> ColorEngine.VideoRange.UNKNOWN })

    internal fun color(info: ColorInfo?)=ColorEngine.VideoColorMetadata(
        when(info?.colorSpace) { null,C.NO_VALUE -> ColorEngine.VideoPrimaries.UNKNOWN
            C.COLOR_SPACE_BT709 -> ColorEngine.VideoPrimaries.REC709
            else -> ColorEngine.VideoPrimaries.OTHER },
        when(info?.colorTransfer) { null,C.NO_VALUE -> ColorEngine.VideoTransfer.UNKNOWN
            C.COLOR_TRANSFER_SDR -> ColorEngine.VideoTransfer.SDR_VIDEO
            C.COLOR_TRANSFER_SRGB -> ColorEngine.VideoTransfer.SRGB
            C.COLOR_TRANSFER_ST2084 -> ColorEngine.VideoTransfer.PQ
            C.COLOR_TRANSFER_HLG -> ColorEngine.VideoTransfer.HLG
            else -> ColorEngine.VideoTransfer.OTHER },
        when(info?.colorRange) { C.COLOR_RANGE_FULL -> ColorEngine.VideoRange.FULL
            C.COLOR_RANGE_LIMITED -> ColorEngine.VideoRange.LIMITED
            else -> ColorEngine.VideoRange.UNKNOWN },info?.hdrStaticInfo?.isNotEmpty() == true)
}
