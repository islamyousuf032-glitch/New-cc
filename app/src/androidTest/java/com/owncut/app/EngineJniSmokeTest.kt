package com.owncut.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineJniSmokeTest {
    @Test
    fun nativeEditingUndoRedoProjectAndAutosaveRoundTrip() {
        val engine = EngineSession()
        val projectFile = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "jni-roundtrip.owncut")
        val projectPath = projectFile.toString()
        val autosaveFile = File("$projectPath.autosave")
        try {
            val mediaIdWithJsonEscapes = "content://test.local/video/quote\"slash\\1"
            val clipId = engine.addMediaClip(mediaIdWithJsonEscapes, 10_000L)
            requireTest(engine.status().contains("clips=1"), "media insert should add one clip")
            requireTestEquals(clipId, engine.clips().single().id)
            requireTestEquals(mediaIdWithJsonEscapes, engine.clips().single().mediaId)
            requireTestEquals(300L, engine.clips().single().durationFrames)

            engine.moveClip(clipId, "v1", 30L)
            requireTestEquals(30L, engine.clips().single().startFrame)
            requireTest(engine.undo(), "move undo should succeed")
            requireTestEquals(0L, engine.clips().single().startFrame)
            requireTest(engine.redo(), "move redo should succeed")
            requireTestEquals(30L, engine.clips().single().startFrame)
            requireTest(engine.undo(), "move redo should be undoable")

            engine.splitClip(clipId, 150L, "right-test-clip")
            requireTest(engine.status().contains("clips=2"), "split should add a clip")
            val right = engine.clips().single { it.id == "right-test-clip" }
            requireTestEquals(150L, right.startFrame)
            requireTestEquals(5_000L, right.sourceInMs)
            requireTest(engine.undo(), "split undo should succeed")
            requireTest(engine.status().contains("clips=1"), "expected one clip")
            requireTest(engine.redo(), "split redo should succeed")
            requireTest(engine.status().contains("clips=2"), "expected two clips")

            engine.trimClip("right-test-clip", true, 6_000L)
            requireTest(engine.undo(), "trim undo should succeed")
            engine.deleteClip("right-test-clip", ripple = true)
            requireTest(engine.status().contains("clips=1"), "expected one clip")
            engine.saveProject(projectPath)
            engine.addMediaClip("content://test.local/video/2", 2_000L)
            requireTest(engine.status().contains("clips=2"), "expected two clips")
            engine.saveAutosave(projectPath)
            engine.loadProject(projectPath)
            requireTest(engine.status().contains("clips=1"), "expected one clip")
            requireTest(!engine.undo(), "loading a project should reset transient undo history")

            requireTest(engine.loadAutosave(projectPath), "autosave snapshot should be available")
            requireTest(engine.status().contains("clips=2"), "expected two clips")
            engine.clearAutosave(projectPath)
            requireTest(!engine.loadAutosave(projectPath), "cleared autosave should not be offered")
        } finally {
            engine.close()
            projectFile.delete()
            autosaveFile.delete()
        }
    }

    private fun requireTest(condition: Boolean, message: String) {
        if (!condition) throw AssertionError(message)
    }

    private fun requireTestEquals(expected: Any?, actual: Any?) {
        if (expected != actual) throw AssertionError("Expected <$expected>, got <$actual>")
    }
}
