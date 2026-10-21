package com.owncut.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Storage transaction for an already rendered/validated file; never makes partial bytes visible. */
internal object ExportPublication {
    interface PendingOutput {
        fun open(): OutputStream
        /** null means the provider cannot report a meaningful size. */
        fun size(): Long?
        fun commit()
        fun discard(): Boolean
    }

    fun publish(file: File, cancelled: AtomicBoolean, create: () -> PendingOutput) {
        checkCancellation(cancelled)
        require(file.isFile && file.length() > 0L) { "সংরক্ষণ করার মতো video data নেই" }
        val expectedSize = file.length()
        val pending = create()
        try {
            var copied = 0L
            pending.open().buffered().use { output ->
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkCancellation(cancelled)
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                    }
                    output.flush()
                }
            } // close/flush errors must also abort the pending row
            check(copied == expectedSize) { "ভিডিও copy অসম্পূর্ণ" }
            pending.size()?.let { check(it == expectedSize) { "সংরক্ষিত ভিডিওর size সঠিক নয়" } }
            checkCancellation(cancelled)
            pending.commit()
        } catch (error: Exception) {
            val removed = try { pending.discard() } catch (cleanup: Exception) {
                error.addSuppressed(cleanup)
                false
            }
            if (!removed) {
                val message = "${error.message ?: "Export ব্যর্থ"} · অসম্পূর্ণ Gallery item সরানো যায়নি"
                if (error is CancellationException) throw CancellationException(message).apply { initCause(error) }
                throw IllegalStateException(message, error)
            }
            throw error
        }
    }

    private fun checkCancellation(cancelled: AtomicBoolean) {
        if (cancelled.get() || Thread.currentThread().isInterrupted)
            throw CancellationException("Export বাতিল হয়েছে")
    }
}

/** Scoped-storage adapter: only our newly inserted pending row is eligible for cleanup. */
@android.annotation.TargetApi(Build.VERSION_CODES.Q)
internal class GalleryPendingOutput private constructor(
    private val context: Context,
    internal val uri: Uri,
) : ExportPublication.PendingOutput {
    override fun open(): OutputStream = context.contentResolver.openOutputStream(uri, "wt")
        ?: throw IllegalStateException("Gallery output-এ লেখা যাচ্ছে না")

    override fun size(): Long? = context.contentResolver.openFileDescriptor(uri, "r")?.use {
        it.statSize.takeIf { size -> size >= 0L }
    }

    override fun commit() {
        check(context.contentResolver.update(uri, ContentValues().apply {
            put(MediaStore.Video.Media.IS_PENDING, 0)
        }, null, null) == 1) { "Gallery-তে output প্রকাশ করা যায়নি" }
    }

    override fun discard(): Boolean = context.contentResolver.delete(uri, null, null) == 1

    companion object {
        fun create(context: Context): GalleryPendingOutput {
            val app = context.applicationContext
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "Owncut-edited-${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Owncut")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = app.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Gallery-তে output তৈরি করা যায়নি")
            return GalleryPendingOutput(app, uri)
        }
    }
}
