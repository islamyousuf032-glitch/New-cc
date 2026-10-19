package com.owncut.app

import android.test.ActivityInstrumentationTestCase2
import android.view.TextureView

@Suppress("DEPRECATION")
class MainActivityLaunchTest : ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
    fun testEditorOpensAndCreatesPreviewTextureView() {
        val launchedActivity = getActivity()
        assertNotNull("MainActivity must launch", launchedActivity)
        assertFalse("MainActivity should remain open", launchedActivity.isFinishing)
        val preview = findView<TextureView>(launchedActivity.window.decorView)
        assertNotNull("Preview TextureView should be present in the initial workspace", preview)
        assertTrue("Activity should be connected to the preview surface callbacks", preview?.surfaceTextureListener === launchedActivity)
    }

    private fun findView(root: android.view.View): TextureView? {
        if (root is TextureView) return root
        if (root is android.view.ViewGroup) {
            for (index in 0 until root.childCount) {
                val found = findView(root.getChildAt(index))
                if (found != null) return found
            }
        }
        return null
    }
}
