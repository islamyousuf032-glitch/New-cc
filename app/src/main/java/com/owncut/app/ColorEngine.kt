package com.owncut.app

import java.nio.ByteBuffer

internal data class ColorChannels(val red: Double, val green: Double, val blue: Double)

/** Immutable settings only. C++ owns validation, transfer functions and all grading math.
 * RGB balance is channel stops, not calibrated Kelvin/tint. Clip/project persistence is owned by the native timeline codec.
 */
internal data class ColorGrade(
    val enabled: Boolean = true,
    val exposureStops: Double = 0.0,
    val contrast: Double = 1.0,
    val saturation: Double = 1.0,
    val pivot: Double = 0.18,
    val strength: Double = 1.0,
    val balanceStops: ColorChannels = ColorChannels(0.0,0.0,0.0),
    val lift: ColorChannels = ColorChannels(0.0,0.0,0.0),
    val gamma: ColorChannels = ColorChannels(1.0,1.0,1.0),
    val gain: ColorChannels = ColorChannels(1.0,1.0,1.0),
) {
    companion object {
        fun fromParametersV1(values: DoubleArray, enabled: Boolean): ColorGrade {
            require(values.size == 17)
            return ColorGrade(enabled,values[0],values[1],values[2],values[3],values[4],
                ColorChannels(values[5],values[6],values[7]),ColorChannels(values[8],values[9],values[10]),
                ColorChannels(values[11],values[12],values[13]),ColorChannels(values[14],values[15],values[16]))
        }
    }

    fun parametersV1(): DoubleArray = doubleArrayOf(exposureStops,contrast,saturation,pivot,strength,
        balanceStops.red,balanceStops.green,balanceStops.blue,lift.red,lift.green,lift.blue,
        gamma.red,gamma.green,gamma.blue,gain.red,gain.green,gain.blue)
}

/** Stateless worker-only frame adapter, NOT wired into live preview/export yet.
 * Buffers must be nonoverlapping, direct, full-capacity slices at position zero.
 * Explicit sRGB straight-alpha RGBA8 only: do not pass YUV, HDR or premultiplied pixels.
 */
internal object ColorEngine {
    init { System.loadLibrary("owncut_android") }

    fun renderRgba8(source: ByteBuffer, output: ByteBuffer, width: Int, height: Int,
                    sourceStride: Int, outputStride: Int, grade: ColorGrade = ColorGrade()) {
        nativeRenderV1(source,output,width,height,sourceStride,outputStride,grade.parametersV1(),grade.enabled)
    }
    private external fun nativeRenderV1(source: ByteBuffer, output: ByteBuffer, width: Int,
        height: Int, sourceStride: Int, outputStride: Int, parameters: DoubleArray, enabled: Boolean)
}
