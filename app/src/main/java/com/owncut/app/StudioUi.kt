package com.owncut.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

internal object StudioUi {
    val background = 0xFF0B1019.toInt()
    val surface = 0xFF151D2B.toInt()
    val raised = 0xFF1E2A3D.toInt()
    val border = 0xFF34445C.toInt()
    val text = 0xFFF0F4FC.toInt()
    val muted = 0xFFAABAD0.toInt()
    val accent = 0xFF8BCBFF.toInt()
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun shape(context: Context, color: Int = surface, radius: Int = 16) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(context, radius).toFloat(); setStroke(dp(context, 1), border)
    }
    fun card(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(context)
        setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
    }
    fun label(context: Context, value: String, size: Float = 14f, secondary: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(if (secondary) muted else StudioUi.text)
        setLineSpacing(dp(context, 3).toFloat(), 1f)
        setPadding(0, dp(context, 4), 0, dp(context, 4))
    }
}

internal enum class StudioIcon { HOME, EDIT, MEDIA, FLOW, EXPORT, SPLIT, TRIM_LEFT, TRIM_RIGHT, DELETE, RIPPLE,
    UNDO, REDO, SAVE, SAVE_AS, OPEN, RECOVER, IMPORT, LINK, PLAY, FOLLOW, CANCEL, PLUS, SETTINGS, RENAME, REFRESH, INFO }

/** Original stroke-vector icons: no icon font, emoji, external assets or proprietary artwork.
 * Button's text is semantic state (TalkBack/tooltip); the visual face draws only the icon.
 */
internal class StudioButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : Button(context, attrs) {
    var icon = StudioIcon.INFO
        set(value) { field = value; invalidate() }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    init {
        val size = StudioUi.dp(context, 52)
        minimumWidth = size; minimumHeight = size
        minWidth = size; minHeight = size
        setPadding(0, 0, 0, 0)
        gravity = Gravity.CENTER
        stateListAnimator = null
        backgroundTintList = null
        background = RippleDrawable(ColorStateList.valueOf(0x338BCBFF), StudioUi.shape(context, StudioUi.surface, 12), null)
        isFocusable = true
    }
    override fun setText(text: CharSequence?, type: android.widget.TextView.BufferType?) {
        super.setText(text, type)
        contentDescription = text
        tooltipText = text
        invalidate()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = StudioUi.dp(context, 52)
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        // View.draw already rendered the RippleDrawable; do not draw the text label.
        val save = canvas.save()
        val scale = StudioUi.dp(context, 24) / 24f
        canvas.translate(width / 2f - 12f * scale, height / 2f - 12f * scale)
        canvas.scale(scale, scale)
        ink.strokeWidth = 1.8f
        ink.color = if (!isEnabled) 0xFF69798F.toInt() else if (isSelected) StudioUi.accent else StudioUi.text
        fun line(x: Float, y: Float, xx: Float, yy: Float) = canvas.drawLine(x,y,xx,yy,ink)
        fun path(vararg values: Float) { val p = Path(); p.moveTo(values[0], values[1]); for(i in 2 until values.size step 2) p.lineTo(values[i],values[i+1]); canvas.drawPath(p,ink) }
        fun box(l: Float,t: Float,r: Float,b: Float) = canvas.drawRoundRect(RectF(l,t,r,b),2f,2f,ink)
        fun arrow(up: Boolean) { val y = if(up) 3f else 21f; val end = if(up) 8f else 16f
            line(12f,y,12f,17f); path(7f,end,12f,y,17f,end) }
        when(icon) {
            StudioIcon.PLAY -> if (text.toString() == AppLanguage.text(R.string.action_pause)) {
                line(8f,5f,8f,19f); line(16f,5f,16f,19f)
            } else path(8f,4f,20f,12f,8f,20f,8f,4f)
            StudioIcon.SPLIT -> { canvas.drawCircle(6f,6f,3f,ink); canvas.drawCircle(6f,18f,3f,ink); line(8f,8f,20f,19f); line(8f,16f,20f,5f) }
            StudioIcon.TRIM_LEFT, StudioIcon.TRIM_RIGHT -> {
                val left = icon == StudioIcon.TRIM_LEFT
                val x = if(left) 5f else 19f; line(x,4f,x,20f); line(4f,12f,20f,12f)
                if(left) path(11f,7f,6f,12f,11f,17f) else path(13f,7f,18f,12f,13f,17f)
            }
            StudioIcon.DELETE -> { path(5f,7f,6f,21f,18f,21f,19f,7f); line(3f,6f,21f,6f); path(8f,6f,8f,3f,16f,3f,16f,6f); line(10f,10f,10f,17f); line(14f,10f,14f,17f) }
            StudioIcon.RIPPLE -> { line(3f,4f,3f,20f); line(21f,4f,21f,20f); path(5f,12f,10f,12f,7f,9f); path(19f,12f,14f,12f,17f,15f) }
            StudioIcon.UNDO, StudioIcon.REDO -> {
                if(icon == StudioIcon.REDO) { canvas.translate(24f,0f); canvas.scale(-1f,1f) }
                path(8f,4f,3f,9f,8f,14f); val p = Path(); p.moveTo(3f,9f); p.cubicTo(21f,2f,23f,21f,10f,20f); canvas.drawPath(p,ink)
            }
            StudioIcon.SAVE, StudioIcon.SAVE_AS -> { path(4f,3f,17f,3f,21f,7f,21f,21f,3f,21f,3f,3f,4f,3f); box(7f,3f,15f,9f); box(7f,14f,17f,21f)
                if(icon == StudioIcon.SAVE_AS) { line(17f,11f,23f,11f); line(20f,8f,20f,14f) } }
            StudioIcon.OPEN, StudioIcon.MEDIA -> { path(3f,8f,3f,4f,9f,4f,12f,7f,21f,7f,21f,19f,3f,19f,3f,8f); line(3f,10f,21f,10f) }
            StudioIcon.IMPORT -> { path(3f,15f,3f,21f,21f,21f,21f,15f); line(12f,3f,12f,16f); path(7f,11f,12f,16f,17f,11f) }
            StudioIcon.EXPORT -> { path(3f,15f,3f,21f,21f,21f,21f,15f); arrow(true) }
            StudioIcon.HOME -> { path(9f,5f,3f,12f,9f,19f); line(3f,12f,21f,12f) }
            StudioIcon.EDIT, StudioIcon.RENAME -> { path(4f,15f,15f,4f,20f,9f,9f,20f,3f,21f,4f,15f); line(12f,7f,17f,12f) }
            StudioIcon.FLOW -> { box(2f,2f,9f,9f); box(15f,15f,22f,22f); path(9f,5f,19f,5f,19f,15f) }
            StudioIcon.RECOVER, StudioIcon.REFRESH -> { canvas.drawArc(RectF(4f,4f,20f,20f),-60f,290f,false,ink); path(15f,2f,20f,5f,16f,9f)
                if(icon == StudioIcon.RECOVER) { line(12f,8f,12f,12f); line(12f,12f,15f,14f) } }
            StudioIcon.LINK -> { canvas.drawOval(RectF(2f,10f,14f,19f),ink); canvas.drawOval(RectF(10f,5f,22f,14f),ink) }
            StudioIcon.FOLLOW -> { canvas.drawCircle(12f,12f,6f,ink); canvas.drawCircle(12f,12f,2f,ink); line(12f,1f,12f,5f); line(12f,19f,12f,23f); line(1f,12f,5f,12f); line(19f,12f,23f,12f) }
            StudioIcon.CANCEL -> { box(5f,5f,19f,19f) }
            StudioIcon.PLUS -> { line(4f,12f,20f,12f); line(12f,4f,12f,20f) }
            StudioIcon.SETTINGS -> { canvas.drawCircle(12f,12f,7f,ink); canvas.drawCircle(12f,12f,2.5f,ink)
                for(i in 0..7) { canvas.save(); canvas.rotate(i*45f,12f,12f); line(12f,2f,12f,5f); canvas.restore() } }
            StudioIcon.INFO -> { canvas.drawCircle(12f,12f,9f,ink); line(12f,11f,12f,17f); canvas.drawCircle(12f,7f,.5f,ink) }
        }
        canvas.restoreToCount(save)
        if (isSelected || isFocused) {
            ink.color = StudioUi.accent; ink.strokeWidth = StudioUi.dp(context,2).toFloat()
            canvas.drawRoundRect(RectF(2f,2f,width-2f,height-2f),StudioUi.dp(context,12).toFloat(),StudioUi.dp(context,12).toFloat(),ink)
        }
    }
}
