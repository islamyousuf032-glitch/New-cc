package com.owncut.app

import java.nio.ByteBuffer

private fun rejected(block: () -> Unit) {
    try { block() } catch (_: IllegalArgumentException) { return }
    error("expected rejected color call")
}
private fun bytes(buffer: ByteBuffer): List<Int> = (0 until buffer.capacity()).map { buffer.get(it).toInt() and 255 }
fun main() {
    val source=ByteBuffer.allocateDirect(8)
    listOf(128,128,128,37,255,0,0,0).forEachIndexed { i,value -> source.put(i,value.toByte()) }
    val out=ByteBuffer.allocateDirect(8)
    val original=bytes(source)
    ColorEngine.renderRgba8(source,out,2,1,8,8,ColorGrade(exposureStops=1.0))
    check(bytes(out)==listOf(176,176,176,37,255,0,0,0))
    ColorEngine.renderRgba8(source.asReadOnlyBuffer(),out,2,1,8,8,ColorGrade(balanceStops=ColorChannels(1.0,0.0,0.0)))
    check(bytes(out).take(4)==listOf(176,128,128,37))
    ColorEngine.renderRgba8(source,out,2,1,8,8,ColorGrade(enabled=false,exposureStops=8.0))
    check(bytes(out)==original)
    val distinct=ColorGrade(exposureStops=0.75,contrast=1.5,saturation=0.8,pivot=0.22,strength=0.6,
        balanceStops=ColorChannels(0.1,0.2,0.3),lift=ColorChannels(0.01,0.02,0.03),
        gamma=ColorChannels(0.8,1.1,1.3),gain=ColorChannels(0.8,1.2,1.4))
    check(distinct.parametersV1().contentEquals(doubleArrayOf(0.75,1.5,0.8,0.22,0.6,
        0.1,0.2,0.3,0.01,0.02,0.03,0.8,1.1,1.3,0.8,1.2,1.4)))
    ColorEngine.renderRgba8(source,out,2,1,8,8,distinct)
    check(out.get(3).toInt()==37 && out.get(7).toInt()==0 && bytes(source)==original)
    val before=bytes(out)
    rejected { ColorEngine.renderRgba8(source,out,2,1,8,8,ColorGrade(gamma=ColorChannels(0.0,1.0,1.0))) }
    rejected { ColorEngine.renderRgba8(source,out,2,1,8,8,ColorGrade(exposureStops=Double.NaN)) }
    rejected { ColorEngine.renderRgba8(source,source,2,1,8,8) }
    rejected { ColorEngine.renderRgba8(source,out.asReadOnlyBuffer(),2,1,8,8) }
    rejected { ColorEngine.renderRgba8(ByteBuffer.allocate(8),out,2,1,8,8) }
    rejected { ColorEngine.renderRgba8(source,out,2,2,8,8) }
    rejected { ColorEngine.renderRgba8(source,out,2,1,7,8) }
    rejected { ColorEngine.renderRgba8(source,out,0,1,8,8) }
    source.position(1); rejected { ColorEngine.renderRgba8(source,out,2,1,8,8) }; source.position(0)
    out.limit(4); rejected { ColorEngine.renderRgba8(source,out,2,1,8,8) }; out.limit(8)
    check(bytes(out)==before && bytes(source)==original)
    println("Production Kotlin -> JNI -> C++: real grading pixels, bypass, RGB parameters, alpha/source preservation and invalid-buffer safety passed")
}
