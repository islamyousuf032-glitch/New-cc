package com.owncut.app

import android.test.InstrumentationTestCase
import java.io.File

class EngineJniSmokeTest : InstrumentationTestCase() {
    fun testNativeEditingUndoRedoProjectAndAutosaveRoundTrip() {
        val engine = EngineSession()
        val projectFile = File(instrumentation.targetContext.filesDir, "jni-roundtrip.owncut")
        val autosaveFile = File("${projectFile.absolutePath}.autosave")
        try {
            val mediaIdWithJsonEscapes = "content://test.local/video/quote\"slash\\1"
            val clipId = engine.addMediaClip(mediaIdWithJsonEscapes, 10_000L)
            assertTrue(engine.status().contains("clips=1"))
            assertEquals(clipId, engine.clips().single().id)
            assertEquals(mediaIdWithJsonEscapes, engine.clips().single().mediaId)
            assertEquals(300L, engine.clips().single().durationFrames)

            engine.splitClip(clipId, 150L, "right-test-clip")
            assertTrue(engine.status().contains("clips=2"))
            val right = engine.clips().single { it.id == "right-test-clip" }
            assertEquals(150L, right.startFrame)
            assertEquals(5_000L, right.sourceInMs)
            assertTrue("split undo should succeed", engine.undo())
            assertTrue(engine.status().contains("clips=1"))
            assertTrue("split redo should succeed", engine.redo())
            assertTrue(engine.status().contains("clips=2"))

            engine.trimClip("right-test-clip", true, 6_000L)
            assertTrue("trim undo should succeed", engine.undo())
            engine.deleteClip("right-test-clip", ripple = true)
            assertTrue(engine.status().contains("clips=1"))
            engine.saveProject(projectFile.absolutePath)
            engine.addMediaClip("content://test.local/video/2", 2_000L)
            assertTrue(engine.status().contains("clips=2"))
            engine.saveAutosave(projectFile.absolutePath)
            engine.loadProject(projectFile.absolutePath)
            assertTrue(engine.status().contains("clips=1"))
            assertFalse("loading a project resets transient undo history", engine.undo())

            assertTrue("autosave snapshot should be available", engine.loadAutosave(projectFile.absolutePath))
            assertTrue(engine.status().contains("clips=2"))
            engine.clearAutosave(projectFile.absolutePath)
            assertFalse("cleared autosave should not be offered", engine.loadAutosave(projectFile.absolutePath))
        } finally {
            engine.close()
            projectFile.delete()
            autosaveFile.delete()
        }
    }
}
