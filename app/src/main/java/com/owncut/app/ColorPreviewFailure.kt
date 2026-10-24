package com.owncut.app

/** Local bounded diagnostics. Never infer missing source metadata from a processing error. */
internal object ColorPreviewFailure {
    fun describe(error: Throwable): String {
        val seen=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable,Boolean>())
        val parts=mutableListOf<String>()
        var current: Throwable?=error
        while(current!=null && parts.size<6 && seen.add(current)) {
            val item=current
            parts.add((item.javaClass.simpleName+": "+(item.message ?: "")).take(300))
            current=item.cause
        }
        return parts.joinToString(" → ")
    }
}
