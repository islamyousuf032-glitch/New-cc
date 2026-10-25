package com.owncut.app

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/** Relative numeric input; NOT a slider. Vertical gestures belong to the controls scroller. */
internal class GradeDragValue(context: Context, private val scale: ColorSliderScale) : TextView(context) {
    var onValue: ((Double) -> Unit)?=null
    var parameterName=""
    private var current=0.0
    private var startX=0f
    private var startY=0f
    private var startProgress=0
    private var dragging=false
    init {
        gravity=android.view.Gravity.CENTER;typeface=android.graphics.Typeface.MONOSPACE
        textSize=15f;setTextColor(StudioUi.accent);background=android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        isFocusable=true;isClickable=true
        minimumHeight=StudioUi.dp(context,48)
    }
    fun bind(value: Double) {
        if(text.isNotEmpty() && value==current) return
        current=value;text=String.format(java.util.Locale.ROOT,"%.3f",value)
        contentDescription="$parameterName $text"
    }
    fun step(delta: Int) = emit((scale.progress(current)+delta).coerceIn(0,ColorSliderScale.STEPS))
    private fun emit(progress: Int) {
        val value=scale.value(progress)
        if(value!=current) { bind(value);onValue?.invoke(value) }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(!isEnabled) return false
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX=event.x;startY=event.y;startProgress=scale.progress(current);dragging=false;isPressed=true;return true }
            MotionEvent.ACTION_MOVE -> {
                val dx=event.x-startX;val dy=event.y-startY
                val slop=ViewConfiguration.get(context).scaledTouchSlop
                if(!dragging && abs(dy)>slop && abs(dy)>abs(dx)) { isPressed=false;return false }
                if(abs(dx)>slop) { dragging=true;parent?.requestDisallowInterceptTouchEvent(true) }
                if(dragging) emit((startProgress+(dx/resources.displayMetrics.density*5).roundToInt()).coerceIn(0,ColorSliderScale.STEPS))
                return true
            }
            MotionEvent.ACTION_UP -> { isPressed=false;parent?.requestDisallowInterceptTouchEvent(false);if(!dragging) performClick();return true }
            MotionEvent.ACTION_CANCEL -> { isPressed=false;parent?.requestDisallowInterceptTouchEvent(false);return true }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick();return true }
    override fun onKeyDown(keyCode: Int,event: KeyEvent): Boolean = when(keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { step(-1);true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { step(1);true }
        else -> super.onKeyDown(keyCode,event)
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.rangeInfo=AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,scale.low.toFloat(),scale.high.toFloat(),current.toFloat())
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }
    override fun performAccessibilityAction(action: Int,arguments: Bundle?): Boolean {
        if(!isEnabled) return false
        return when(action) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
                if(arguments==null || !arguments.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)) false
                else {
                    val requested=arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE).toDouble()
                    if(!requested.isFinite()) false else { emit(scale.progress(requested.coerceIn(scale.low,scale.high)));true }
                }
            }
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { step(1);true }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { step(-1);true }
            else -> super.performAccessibilityAction(action,arguments)
        }
    }
}
