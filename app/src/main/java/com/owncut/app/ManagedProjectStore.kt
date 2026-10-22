package com.owncut.app

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.security.MessageDigest

/** Single-writer platform storage seam; native engine still owns codec and edit semantics.
 * Acquire/use/close on workers. Retain the same owner across Activity recreation.
 */
internal class ManagedProjectStore private constructor(private val file: File, private val lockFile: RandomAccessFile, private val lease: FileLock) : AutoCloseable {
    private var expected: String? = null
    private var loaded = false
    private var closed = false
    @Synchronized fun load(session: EngineSession) {
        checkOpen()
        stableSnapshot(file) { snapshot, hash ->
            session.loadProject(snapshot.absolutePath) // native parse before live-state replacement
            expected = hash
            loaded = true
        }
    }
    @Synchronized fun checkpoint(session: EngineSession) {
        checkOpen(); check(loaded) { AppLanguage.text(R.string.project_not_loaded) }
        check(expected == fingerprint()) { AppLanguage.text(R.string.project_conflict) }
        session.saveProject(file.absolutePath)
        // Keep recovery until successful native save and a fresh readable validation.
        EngineSession().use { it.loadProject(file.absolutePath) }
        expected = fingerprint()
        session.clearAutosave(file.absolutePath)
    }
    @Synchronized fun autosave(session: EngineSession, canWrite: () -> Boolean = { true }) {
        checkOpen(); check(loaded)
        if (canWrite()) session.saveAutosave(file.absolutePath)
    }
    @Synchronized fun recover(session: EngineSession): Boolean {
        checkOpen(); check(loaded)
        val recovery = File(file.absolutePath + ".autosave")
        if (!recovery.exists()) return false
        stableSnapshot(recovery) { snapshot, _ -> session.loadProject(snapshot.absolutePath) }
        return true
    }
    private fun checkOpen() { check(!closed) { "Managed store closed" } }
    /** No live-state mutation until the exact bounded snapshot passes revision checks. */
    private fun stableSnapshot(source: File, consume: (File, String) -> Unit) {
        val before = requireNotNull(fingerprint(source)) { "Project file is missing" }
        val snapshot = File.createTempFile("read-", ".owncut", file.parentFile)
        try {
            source.inputStream().buffered().use { input ->
                snapshot.outputStream().buffered().use { output -> ProjectDocumentIo.copy(input,output) }
            }
            check(before == fingerprint(source) && before == fingerprint(snapshot)) { AppLanguage.text(R.string.project_conflict) }
            consume(snapshot,before)
        } finally { snapshot.delete() }
    }
    private fun fingerprint(file: File = this.file): String? {
        if (!file.exists()) return null
        require(file.length() <= ProjectDocumentIo.MAX_PROJECT_BYTES) { "Project size exceeds safety limit" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64*1024)
            var total = 0L
            while(true) { val count = input.read(buffer); if(count < 0) break; total += count
                require(total <= ProjectDocumentIo.MAX_PROJECT_BYTES); digest.update(buffer,0,count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    @Synchronized override fun close() {
        if(closed) return
        closed = true
        try { lease.release() } finally { lockFile.close() }
    }
    companion object {
        fun acquire(file: File): ManagedProjectStore {
            val owner = RandomAccessFile(File(file.parentFile,"writer.lock"),"rw")
            try {
                val lease = try { owner.channel.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
                check(lease != null) { AppLanguage.text(R.string.project_already_open) }
                return ManagedProjectStore(file,owner,lease)
            } catch(error: Exception) { owner.close(); throw error }
        }
    }
}
