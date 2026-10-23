package com.owncut.app

import java.util.concurrent.CancellationException

private fun rejected(block: () -> Unit) {
    try { block() } catch (_: IllegalArgumentException) { return }
    error("expected invalid color plan rejection")
}
fun main() {
    val supported=ColorEngine.VideoColorMetadata(ColorEngine.VideoPrimaries.REC709,
        ColorEngine.VideoTransfer.SDR_VIDEO,ColorEngine.VideoRange.LIMITED)
    for(p in ColorEngine.VideoPrimaries.entries) for(t in ColorEngine.VideoTransfer.entries)
        for(r in ColorEngine.VideoRange.entries) {
            val source=ColorEngine.VideoColorMetadata(p,t,r)
            if (p==supported.primaries && t==supported.transfer && r!=ColorEngine.VideoRange.UNKNOWN)
                ColorEngine.validateVideoSource(source)
            else rejected { ColorEngine.validateVideoSource(source) }
        }
    rejected { ColorEngine.validateVideoSource(supported.copy(hasHdrStaticMetadata=true)) }
    val grade=ColorGrade(exposureStops=1.0)
    val first=ClipSnapshot("first","v1","content://shared",0,30,0,1000,grade,true)
    val second=first.copy(id="second",startFrame=30,sourceInMs=1000,sourceOutMs=2000,
        grade=grade.copy(enabled=false),gradeActive=false)
    var reads=0
    fun read(uri: String): VideoSourceMetadata {
        reads++;return VideoSourceMetadata(uri,1,1920,1080,90,supported)
    }
    val mutable=mutableListOf(first,second)
    val plan=ColorRenderPlan.prepare("42",mutable,{},::read)
    check(reads==1 && plan.revision=="42" && plan.clips.map {it.clip}==mutable)
    check(plan.clips[0].shaders.fragment.contains("toLight") && !plan.clips[1].shaders.fragment.contains("toLight"))
    check(plan.clips[0].source === plan.clips[1].source)
    mutable.clear();check(plan.clips.size==2 && plan.clips[0].clip.grade==grade)
    try { (plan.clips as MutableList).clear();error("mutable plan escaped") } catch (_: UnsupportedOperationException) { }
    rejected { ColorRenderPlan.prepare("",listOf(first),{},::read) }
    rejected { ColorRenderPlan.prepare("42",listOf(first,first),{},::read) }
    rejected { ColorRenderPlan.prepare("42",listOf(first),{}) { read(it).copy(mediaId="wrong") } }
    rejected { ColorRenderPlan.prepare("42",listOf(first),{}) { read(it).copy(width=0) } }
    rejected { ColorRenderPlan.prepare("42",listOf(first),{}) { read(it).copy(rotationDegrees=45) } }
    rejected { ColorRenderPlan.prepare("42",listOf(first),{}) { read(it).copy(color=supported.copy(transfer=ColorEngine.VideoTransfer.HLG)) } }
    rejected { ColorRenderPlan.prepare("42",listOf(first),{}) { read(it).copy(videoTrackIndex=-1) } }
    rejected { ColorRenderPlan.prepare("42",listOf(first),{}) { read(it).copy(height=0) } }
    for (cancelAt in 1..4) {
        var checks=0
        try { ColorRenderPlan.prepare("42",listOf(first),{
            checks++;if(checks==cancelAt) throw CancellationException()
        },::read);error("cancelled preparation published at stage $cancelAt") }
        catch (_: CancellationException) { check(checks==cancelAt) }
    }
    var cancelled=false
    try { ColorRenderPlan.prepare("42",listOf(first),{ if(cancelled) throw CancellationException() }) {
        cancelled=true;read(it)
    };error("cancelled result published") } catch (_: CancellationException) { }
    var attempted=false
    try { ColorRenderPlan.prepare("42",listOf(first),{throw CancellationException()}) {
        attempted=true;read(it)
    };error("cancelled probe started") } catch (_: CancellationException) { }
    check(!attempted)
    val bad=second.copy(grade=grade.copy(gamma=ColorChannels(0.0,1.0,1.0)))
    rejected { ColorRenderPlan.prepare("43",listOf(first,bad),{},::read) }
    val again=ColorRenderPlan.prepare("44",listOf(first),{},::read)
    check(again.revision=="44" && plan.revision=="42") // failure did not poison/publish cache
    check(reads>1) // no cross-request URI cache/freshness claim
    println("Production Kotlin/native source policy and shared plan: immutable per-clip recipes, request-local dedup, cancellation/failure atomicity passed")
}
