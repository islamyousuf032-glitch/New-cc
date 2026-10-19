package com.owncut.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectDocumentIoTest {
    @Test
    fun copiesProjectBytesExactly() {
        val source = "OWNCUTPJ\u0001project-data".toByteArray(Charsets.UTF_8)
        val destination = ByteArrayOutputStream()

        ProjectDocumentIo.copy(ByteArrayInputStream(source), destination, maxBytes = source.size.toLong())

        assertArrayEquals(source, destination.toByteArray())
    }

    @Test
    fun rejectsInputBeyondBound() {
        val destination = ByteArrayOutputStream()
        var rejected = false
        try {
            ProjectDocumentIo.copy(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)), destination, maxBytes = 4)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertEquals(true, rejected)
        assertEquals(0, destination.size())
    }
}
