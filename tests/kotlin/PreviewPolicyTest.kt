package com.owncut.app

private fun refused(block: () -> Unit) {
    try { block() } catch (_: IllegalArgumentException) { return }
    error("invalid preview policy accepted")
}
fun main() {
    val s=PreviewPlaybackState()
    check(!s.canTransport && !s.canSeek && !s.canCompare(true))
    check(s.buffering() && s.canTransport && !s.canSeek)
    check(s.ready() && s.canSeek && s.canCompare(true) && !s.canCompare(false))
    check(s.buffering() && !s.canCompare(true))
    check(s.fail() && !s.fail())
    check(!s.ready() && !s.buffering() && !s.loading() && !s.canTransport && !s.canSeek)
    check(s.close() && !s.close() && !s.ready() && !s.fail())
    check(s.phase==PreviewPlaybackState.Phase.CLOSED)
    val beforeReady=PreviewPlaybackState();beforeReady.close();check(!beforeReady.ready())
    repeat(500) { seed ->
        val state=PreviewPlaybackState()
        var terminal=false
        repeat(50) { tick ->
            when((seed*17+tick*31)%5) {
                0 -> state.loading();1 -> state.buffering();2 -> state.ready();3 -> state.fail();else -> state.close()
            }
            if(terminal) check(state.terminal)
            terminal=terminal || state.terminal
            if(terminal) check(!state.canSeek && !state.canCompare(true) && !state.canTransport)
        }
    }
    check(PreviewProgress.seekPosition(1000,1000)==999L)
    check(PreviewProgress.seekPosition(1,1000)==0L)
    check(PreviewProgress.seekPosition(Long.MAX_VALUE,1000)==Long.MAX_VALUE-1)
    check(PreviewProgress.clipRelative(Long.MIN_VALUE,100,200)==0L)
    check(PreviewProgress.clipRelative(Long.MAX_VALUE,100,200)==99L)
    check(PreviewProgress.clipRelative(150,100,200)==50L)
    refused { PreviewProgress.seekPosition(0,0) };refused { PreviewProgress.clipRelative(100,100,100) }
    check(PreviewFrameSize.fit(3840,2160)==PreviewFrameSize.Size(1280,720))
    check(PreviewFrameSize.fit(1920,1080,90)==PreviewFrameSize.Size(405,720))
    check(PreviewFrameSize.fit(640,480)==PreviewFrameSize.Size(640,480))
    check(PreviewFrameSize.fitsUnscaled(640,480,90))
    check(!PreviewFrameSize.fitsUnscaled(1920,1080,0))
    for(w in listOf(1,128,640,3840,Int.MAX_VALUE)) for(h in listOf(1,128,480,2160,Int.MAX_VALUE))
        for(r in listOf(0,90,180,270)) {
            val size=PreviewFrameSize.fit(w,h,r)
            check(size.width in 1..1280 && size.height in 1..720)
            val orientedWidth=if(r==90 || r==270) h else w
            val orientedHeight=if(r==90 || r==270) w else h
            check(size.width<=orientedWidth && size.height<=orientedHeight)
        }
    refused { PreviewFrameSize.fit(0,100) };refused { PreviewFrameSize.fit(100,100,45) }
    println("Production preview policy: terminal failures/close, late events, control gates, exclusive seeks and bounded frame requests passed")
}
