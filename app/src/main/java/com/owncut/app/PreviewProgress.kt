package com.owncut.app

/** Seek-bar transport only; codec milliseconds stay unchanged (no timeline/frame edits).
 * Exact overflow-safe floor(duration * progress / 1000), even at Long.MAX_VALUE.
 */
internal object PreviewProgress {
    fun position(durationMs: Long, progress: Int): Long {
        require(durationMs>=0 && progress in 0..1000)
        return (durationMs/1000)*progress + (durationMs%1000)*progress/1000
    }
    // Clip out is exclusive: 100% must not seek to EOS and unexpectedly loop/restart.
    fun seekPosition(durationMs: Long, progress: Int): Long {
        require(durationMs>0)
        return position(durationMs,progress).coerceAtMost(durationMs-1)
    }
    fun clipRelative(sourcePositionMs: Long, sourceInMs: Long, sourceOutMs: Long): Long {
        require(sourceInMs>=0 && sourceOutMs>sourceInMs)
        return sourcePositionMs.coerceIn(sourceInMs,sourceOutMs-1)-sourceInMs
    }
    fun progress(positionMs: Long, durationMs: Long): Int =
        if(durationMs<=0) 0 else (positionMs.coerceIn(0,durationMs).toDouble()/durationMs*1000).toInt().coerceIn(0,1000)
}
