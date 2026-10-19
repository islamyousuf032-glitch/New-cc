package com.owncut.app

import android.test.ActivityInstrumentationTestCase2
import android.view.TextureView

@Suppress("DEPRECATION")
class MainActivityLaunchTest : ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
    fun testEditorOpensAndCreatesPreviewTextureView() {
        val launchedActivity = getActivity()
        check(!launchedActivity.isFinishing) { "MainActivity should remain open after launch" }
        val preview = findView<TextureView>(launchedActivity.window.decorView)
            ?: throw AssertionError("Preview TextureView is missing from the initial workspace")
        check(preview.surfaceTextureListener === launchedActivity) {
            "Activity is not connected to the preview surface callbacks"
        }
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
