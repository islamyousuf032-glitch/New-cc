package com.owncut.app

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.SeekBar
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test

/** Source compiled by CI; actual UI execution is an opt-in device check, not host proof. */
@UnstableApi
class ColorEditorInteractionTest {
    private fun children(view: View): List<View> = listOf(view)+if(view is ViewGroup)
        (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()
    private val missing=ClipSnapshot("test","v1","file:///owncut-nonexistent-test-source.mp4",0,30,0,1000)

    @Test fun slidersAndApplyDoNotRequireAWorkingStillDecoder() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var saved: ColorGrade?=null
                val dialog=ClipColorEditor.show(activity,missing,0) { saved=it }
                try {
                    val views=children(requireNotNull(dialog.window).decorView)
                    assertTrue(views.none { it is EditText })
                    val sliders=views.filterIsInstance<SeekBar>()
                    assertEquals(17,sliders.size)
                    val argument=Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,1500f) }
                    assertTrue(sliders.first().performAccessibilityAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,argument))
                    assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                    assertEquals(2.0,requireNotNull(saved).exposureStops,0.000001)
                    assertFalse(dialog.isShowing)
                } finally { dialog.dismiss() }
            }
        }
    }

    @Test fun referenceIsImmediatelyVisibleOutsideControlsScrollerAndFineStepsSave() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val reference=android.graphics.Bitmap.createBitmap(64,36,android.graphics.Bitmap.Config.ARGB_8888)
                reference.eraseColor(android.graphics.Color.DKGRAY)
                var saved: ColorGrade?=null
                val dialog=ClipColorEditor.show(activity,missing,0,referenceFrame=reference) { saved=it }
                try {
                    val views=children(requireNotNull(dialog.window).decorView)
                    val image=views.filterIsInstance<android.widget.ImageView>().single {
                        it.contentDescription==AppLanguage.text(R.string.color_frame_viewer)
                    }
                    assertSame(reference,(image.drawable as android.graphics.drawable.BitmapDrawable).bitmap)
                    var parent=image.parent
                    while(parent is View) {
                        assertFalse("Viewer must not scroll with controls",parent is android.widget.ScrollView)
                        parent=parent.parent
                    }
                    val sliders=views.filterIsInstance<GradePrecisionSlider>()
                    assertEquals(17,sliders.size)
                    val initial=sliders.first().progress
                    sliders.first().step(1)
                    assertEquals(initial+1,sliders.first().progress)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                    assertEquals(0.004,requireNotNull(saved).exposureStops,0.000001)
                } finally { dialog.dismiss() }
            }
        }
    }

    @Test fun failedCommitDoesNotPretendToSaveOrDismiss() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog=ClipColorEditor.show(activity,missing,0) { error("mock commit rejected") }
                try {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                    assertTrue(dialog.isShowing)
                    assertTrue(children(requireNotNull(dialog.window).decorView)
                        .filterIsInstance<android.widget.TextView>().any { it.text.contains("mock commit rejected") })
                } finally { dialog.dismiss() }
            }
        }
    }
}
