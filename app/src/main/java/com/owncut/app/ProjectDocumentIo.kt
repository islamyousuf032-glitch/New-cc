package com.owncut.app

import java.io.InputStream
import java.io.OutputStream

/** Bounded stream copy used by the SAF adapter; the native project codec itself is path-based. */
internal object ProjectDocumentIo {
    const val MAX_PROJECT_BYTES = 96L * 1024L * 1024L

    fun copy(input: InputStream, output: OutputStream, maxBytes: Long = MAX_PROJECT_BYTES) {
        require(maxBytes >= 0L) { "maxBytes must not be negative" }
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "Project file exceeds the ${maxBytes / (1024 * 1024)} MiB limit" }
            output.write(buffer, 0, count)
        }
        output.flush()
    }
}
