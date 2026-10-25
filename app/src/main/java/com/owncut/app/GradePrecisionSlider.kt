package com.owncut.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.widget.SeekBar

/** Original ruler scrubber, not a grading algorithm. Retains native SeekBar keyboard,
 * accessibility, touch/scroll arbitration; one-step buttons provide fine adjustments.
 */
internal class GradePrecisionSlider(context: Context) : SeekBar(context) {
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG)
    var neutralProgress: Int=0
        set(value) { field=value;invalidate() }
    var railColor: Int=StudioUi.accent
        set(value) { field=value;invalidate() }
    var onStep: ((Int) -> Unit)?=null
    private fun dp(value: Float)=value*resources.displayMetrics.density
    init {
        max=ColorSliderScale.STEPS
        minimumHeight=dp(48f).toInt()
        setPadding(dp(12f).toInt(),0,dp(12f).toInt(),0)
        progressDrawable=ColorDrawable(android.graphics.Color.TRANSPARENT)
        thumb=GradientDrawable().apply {
            shape=GradientDrawable.OVAL;setColor(android.graphics.Color.TRANSPARENT)
            setSize(dp(20f).toInt(),dp(20f).toInt())
        }
        thumbOffset=dp(10f).toInt()
        setKeyProgressIncrement(1)
        splitTrack=false
    }
    fun step(delta: Int) {
        if(!isEnabled) return
        val next=(progress+delta).coerceIn(0,max)
        if(next!=progress) { progress=next;onStep?.invoke(next) }
    }
    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
        setMeasuredDimension(measuredWidth,resolveSize(dp(48f).toInt(),heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        val left=paddingLeft.toFloat();val right=(width-paddingRight).toFloat()
        if(right<=left || max<=0) return
        val center=height/2f
        fun x(value: Int): Float {
            val fraction=value.toFloat()/max
            return if(layoutDirection==LAYOUT_DIRECTION_RTL) right-(right-left)*fraction else left+(right-left)*fraction
        }
        ink.color=StudioUi.raised
        canvas.drawRoundRect(left,dp(3f),right,height-dp(3f),dp(9f),dp(9f),ink)
        ink.strokeWidth=dp(1f);ink.color=StudioUi.border
        for(index in 0..20) {
            val tick=x(max*index/20)
            val size=dp(if(index%5==0) 6f else 3f)
            canvas.drawLine(tick,center-size,tick,center+size,ink)
        }
        ink.strokeWidth=dp(2f)
        canvas.drawLine(left,center,right,center,ink)
        val neutral=x(neutralProgress.coerceIn(0,max));val cursor=x(progress)
        ink.color=if(isEnabled) railColor else StudioUi.muted
        canvas.drawLine(neutral,center,cursor,center,ink)
        ink.color=StudioUi.muted;ink.strokeWidth=dp(1f)
        canvas.drawLine(neutral,center-dp(10f),neutral,center+dp(10f),ink)
        ink.color=if(isEnabled) railColor else StudioUi.muted
        canvas.drawRoundRect(cursor-dp(2.5f),center-dp(13f),cursor+dp(2.5f),center+dp(13f),dp(2f),dp(2f),ink)
    }
}
