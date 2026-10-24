package com.owncut.app

/** Seek-bar transport only; codec milliseconds stay unchanged (no timeline/frame edits).
 * Exact overflow-safe floor(duration * progress / 1000), even at Long.MAX_VALUE.
 */
internal object PreviewProgress {
    fun position(durationMs: Long, progress: Int): Long {
        require(durationMs>=0 && progress in 0..1000)
        return (durationMs/1000)*progress + (durationMs%1000)*progress/1000
    }
    fun progress(positionMs: Long, durationMs: Long): Int =
        if(durationMs<=0) 0 else (positionMs.coerceIn(0,durationMs).toDouble()/durationMs*1000).toInt().coerceIn(0,1000)
}
