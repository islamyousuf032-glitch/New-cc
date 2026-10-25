package com.owncut.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View

/** Original color-disc artwork and pointer transport. Native C++ owns RGB mapping. */
internal class GradeColorWheel(context: Context) : View(context) {
    var onPoint: ((Double,Double) -> Unit)?=null
    var onBegin: (() -> Unit)?=null
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG)
    private var px=0f
    private var py=0f
    private var captured=false
    private var hue: Shader?=null
    private var neutral: Shader?=null
    init { isFocusable=false;minimumHeight=StudioUi.dp(context,140);importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun bind(point: DoubleArray) {
        require(point.size==2)
        val x=point[0].toFloat();val y=point[1].toFloat()
        if(px==x && py==y) return
        px=x;py=y;invalidate()
    }
    private fun radius()=minOf(width,height)*0.43f
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        val x=w/2f;val y=h/2f;val r=radius()
        if(r<=0) return
        hue=SweepGradient(x,y,intArrayOf(0xffde716b.toInt(),0xff6a8bdf.toInt(),0xff78bf98.toInt(),0xffde716b.toInt()),floatArrayOf(0f,1f/3,2f/3,1f))
        neutral=RadialGradient(x,y,r,intArrayOf(StudioUi.surface,StudioUi.surface and 0x00ffffff),null,Shader.TileMode.CLAMP)
    }
    override fun onDraw(canvas: Canvas) {
        val x=width/2f;val y=height/2f;val r=radius()
        if(r<=0) return
        ink.shader=hue
        canvas.drawCircle(x,y,r,ink)
        ink.shader=neutral
        canvas.drawCircle(x,y,r,ink);ink.shader=null
        ink.color=StudioUi.muted;ink.strokeWidth=resources.displayMetrics.density
        canvas.drawLine(x-5,y,x+5,y,ink);canvas.drawLine(x,y-5,x,y+5,ink)
        ink.style=Paint.Style.STROKE;ink.color=StudioUi.border;canvas.drawCircle(x,y,r,ink)
        ink.strokeWidth=resources.displayMetrics.density*2;ink.color=Color.WHITE
        canvas.drawCircle(x+px*r,y+py*r,resources.displayMetrics.density*6,ink)
        ink.style=Paint.Style.FILL
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(!isEnabled || radius()<=0) return false
        if(event.actionMasked==MotionEvent.ACTION_DOWN) {
            val dx=event.x-width/2f;val dy=event.y-height/2f
            if(dx*dx+dy*dy>radius()*radius()) return false
            captured=true;parent?.requestDisallowInterceptTouchEvent(true);onBegin?.invoke()
        }
        if(!captured) return false
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE -> {
                var x=((event.x-width/2f)/radius()).coerceIn(-1f,1f)
                var y=((event.y-height/2f)/radius()).coerceIn(-1f,1f)
                val length=kotlin.math.hypot(x,y)
                if(length>1) { x/=length;y/=length }
                onPoint?.invoke(x.toDouble(),y.toDouble())
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                captured=false;parent?.requestDisallowInterceptTouchEvent(false)
                if(event.actionMasked==MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick();return true }
}
