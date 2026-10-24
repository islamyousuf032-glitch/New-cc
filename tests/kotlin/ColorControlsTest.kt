package com.owncut.app

fun main() {
    val defaults=doubleArrayOf(0.0,1.0,1.0,0.18,1.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,1.0,1.0,1.0,1.0,1.0)
    defaults.forEachIndexed { index,saved ->
        val scale=ColorSliderScale.forParameter(index,saved)
        check(scale.progress(saved) in 0..ColorSliderScale.STEPS)
        check(kotlin.math.abs(scale.value(scale.progress(saved))-saved)<=(scale.high-scale.low)/ColorSliderScale.STEPS+1e-12)
        var previous=scale.low
        for(step in 0..ColorSliderScale.STEPS) {
            val value=scale.value(step)
            check(value>=previous && value in scale.low..scale.high)
            previous=value
        }
    }
    // Previously saved values outside practical UI defaults must NOT be silently clamped.
    for((index,saved) in listOf(0 to -8.0,0 to 8.0,1 to 4.0,8 to -1.0,11 to 4.0,14 to 4.0)) {
        val scale=ColorSliderScale.forParameter(index,saved)
        check(saved in scale.low..scale.high)
        check(kotlin.math.abs(scale.value(scale.progress(saved))-saved)<1e-9)
    }
    fun rejected(block: () -> Unit) { check(runCatching(block).isFailure) }
    rejected { ColorSliderScale.forParameter(17,0.0) }
    rejected { ColorSliderScale.forParameter(0,Double.NaN) }
    rejected { ColorSliderScale(0.0,0.0) }
    rejected { ColorSliderScale(0.0,1.0).value(-1) }
    val root=IllegalStateException("raw decoder color missing")
    val wrapped=RuntimeException("Video frame processing error",root)
    check(ColorPreviewFailure.describe(wrapped).contains(root.message!!))
    val long=RuntimeException("x".repeat(10000))
    check(ColorPreviewFailure.describe(long).length<=300)
    val a=RuntimeException("A");val b=RuntimeException("B",a);a.initCause(b)
    check(ColorPreviewFailure.describe(a).length<100)
    println("Production color controls: 34017 slider steps, saved-range preservation, bounded nested/cyclic diagnostics passed")
}
