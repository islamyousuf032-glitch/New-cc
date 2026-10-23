package com.owncut.app

import java.nio.ByteBuffer

/** Android Bitmap.getPixels straight ARGB sRGB ints -> native explicit RGBA8 -> ARGB.
 * Pixel transport only, no transfer/grade math. Not raw codec/YUV/HDR/premultiplied data.
 */
internal object ColorFrameBuffers {
    fun render(source: IntArray, width: Int, height: Int, grade: ColorGrade): IntArray {
        require(width>0 && height>0 && Math.multiplyExact(width,height)==source.size)
        val bytes=Math.multiplyExact(source.size,4)
        val input=ByteBuffer.allocateDirect(bytes)
        source.forEach { p -> input.put((p shr 16).toByte());input.put((p shr 8).toByte());input.put(p.toByte());input.put((p ushr 24).toByte()) }
        input.position(0)
        val output=ByteBuffer.allocateDirect(bytes)
        ColorEngine.renderRgba8(input,output,width,height,Math.multiplyExact(width,4),Math.multiplyExact(width,4),grade)
        return IntArray(source.size) { i ->
            val o=i*4
            ((output.get(o+3).toInt() and 255) shl 24) or ((output.get(o).toInt() and 255) shl 16) or
                ((output.get(o+1).toInt() and 255) shl 8) or (output.get(o+2).toInt() and 255)
        }
    }
}
