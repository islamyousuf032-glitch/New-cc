package com.owncut.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Original Owncut ruler/lanes/playhead, all drawn in one shared scroll coordinate space. */
internal class TimelineView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = Path()
    private val red = 0xFFFF5964.toInt()
    private val rulerHeight = 34f * density
    private val laneHeight = 72f * density
    private var tracks = emptyList<String>()
    internal var clips = emptyList<ClipSnapshot>(); private set
    internal var endFrame = 0L; private set
    internal var playheadFrame = 0L; private set
    internal var pixelsPerFrame = 1.0; private set
    private var selectedId: String? = null
    private var selectedTrack: String? = null
    private var draggingHead = false
    private var movingClip: ClipSnapshot? = null
    private var downX = 0f
    private var moveDelta = 0f
    private var focusedVirtualId = View.NO_ID
    var onSeek: (String, Long) -> Unit = { _, _ -> }
    var onScrub: (Long, Boolean) -> Unit = { _, _ -> }
    var onScrubCancel: () -> Unit = {}
    var onMove: (ClipSnapshot, Long) -> Unit = { _, _ -> }

    init {
        isFocusable = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Timeline; ruler-এ ট্যাপ বা লাল playhead ধরে seek করুন"
    }

    fun setTimeline(values: List<ClipSnapshot>, selected: String?) {
        clips = values
        tracks = values.map { it.trackId }.distinct().sorted()
        selectedId = selected
        selectedTrack = values.firstOrNull { it.id == selected }?.trackId
        endFrame = values.maxOfOrNull { Math.addExact(it.startFrame, it.durationFrames) } ?: 0L
        pixelsPerFrame = TimelineGeometry.pixelsPerFrame(endFrame, density)
        playheadFrame = playheadFrame.coerceIn(0L, endFrame)
        requestLayout()
        invalidate()
    }

    fun setPlayhead(frame: Long) {
        playheadFrame = frame.coerceIn(0L, endFrame)
        invalidate()
    }

    fun xForFrame(frame: Long): Float = (frame * pixelsPerFrame).toFloat()
    private fun frameAt(x: Float) = TimelineGeometry.frameAt(x, pixelsPerFrame, endFrame)
    private fun trackAt(y: Float): String? = if (y < rulerHeight) selectedTrack ?: tracks.firstOrNull()
        else tracks.getOrNull(((y - rulerHeight) / laneHeight).toInt())
    private fun clipRect(clip: ClipSnapshot): RectF {
        val lane = tracks.indexOf(clip.trackId)
        val top = rulerHeight + lane * laneHeight + 17f * density
        return RectF(xForFrame(clip.startFrame), top,
            xForFrame(clip.startFrame + clip.durationFrames).coerceAtLeast(xForFrame(clip.startFrame) + 1f),
            top + 47f * density)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wantedWidth = ceil(xForFrame(endFrame) + 24f * density).toInt().coerceAtLeast((240 * density).toInt())
        setMeasuredDimension(resolveSize(wantedWidth.coerceAtLeast(suggestedMinimumWidth), widthMeasureSpec),
            resolveSize((rulerHeight + tracks.size.coerceAtLeast(1) * laneHeight).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF18221C.toInt())
        val visible = canvas.clipBounds
        paint.color = 0xFF91A99A.toInt()
        paint.textSize = 10f * density
        val minTickFrames = (72.0 * density / pixelsPerFrame).coerceAtLeast(30.0)
        var tickFrames = 30L
        while (tickFrames < minTickFrames && tickFrames <= Long.MAX_VALUE / 2L) tickFrames *= 2L
        var tick = (frameAt(visible.left.toFloat()) / tickFrames) * tickFrames
        while (tick <= endFrame && xForFrame(tick) <= visible.right + 100f * density) {
            val x = xForFrame(tick)
            paint.color = 0xFF91A99A.toInt()
            canvas.drawLine(x, rulerHeight - 8 * density, x, rulerHeight, paint)
            canvas.drawText(TimelineGeometry.timecode(tick).substringBeforeLast(':'), x + 3 * density, 14 * density, paint)
            if (endFrame - tick < tickFrames) break
            tick += tickFrames
        }
        for ((lane, track) in tracks.withIndex()) {
            paint.color = 0xFF91A99A.toInt()
            canvas.drawText("TRACK · $track", visible.left + 4f * density, rulerHeight + lane * laneHeight + 12 * density, paint)
        }
        for (clip in clips) {
            val rect = clipRect(clip)
            if (movingClip?.id == clip.id) rect.offset(moveDelta, 0f)
            if (rect.right < visible.left || rect.left > visible.right) continue
            paint.color = if (clip.id == selectedId) 0xFF29634A.toInt() else 0xFF294638.toInt()
            canvas.drawRoundRect(rect, 4 * density, 4 * density, paint)
            if (clip.id == selectedId) {
                paint.color = 0xFF8BC8A8.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = density
                canvas.drawRoundRect(rect, 4 * density, 4 * density, paint); paint.style = Paint.Style.FILL
            }
            canvas.save()
            canvas.clipRect(rect)
            paint.color = 0xFFE5EEE8.toInt(); paint.textSize = 11f * density
            canvas.drawText(clip.id.take(16), rect.left + 5 * density, rect.top + 19 * density, paint)
            paint.textSize = 9f * density
            canvas.drawText(TimelineGeometry.timecode(clip.durationFrames), rect.left + 5 * density, rect.top + 36 * density, paint)
            canvas.restore()
        }
        val headX = xForFrame(playheadFrame)
        paint.color = red; paint.strokeWidth = 2f * density
        canvas.drawLine(headX, 18 * density, headX, height.toFloat(), paint)
        marker.reset(); marker.moveTo(headX - 6 * density, 17 * density)
        marker.lineTo(headX + 6 * density, 17 * density); marker.lineTo(headX, 25 * density); marker.close()
        canvas.drawPath(marker, paint)
    }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            performClick()
            trackAt(e.y)?.let { onSeek(it, frameAt(e.x)) }
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            if (e.y < rulerHeight) return
            movingClip = clips.lastOrNull { clipRect(it).contains(e.x, e.y) }
            if (movingClip != null) {
                parent?.requestDisallowInterceptTouchEvent(true)
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || clips.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                draggingHead = event.y < rulerHeight && kotlin.math.abs(event.x - xForFrame(playheadFrame)) < 22f * density
                if (draggingHead) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    onScrub(frameAt(event.x), false)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingHead) { setPlayhead(frameAt(event.x)); onScrub(playheadFrame, false); return true }
                if (movingClip != null) { moveDelta = event.x - downX; invalidate(); return true }
            }
            MotionEvent.ACTION_UP -> {
                if (draggingHead) {
                    draggingHead = false; performClick(); onScrub(frameAt(event.x), true); return true
                }
                movingClip?.let { clip ->
                    gestures.onTouchEvent(event)
                    val proposed = (clip.startFrame + (moveDelta / pixelsPerFrame).roundToLong()).coerceAtLeast(0L)
                    movingClip = null; moveDelta = 0f; invalidate(); onMove(clip, proposed); return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                gestures.onTouchEvent(event)
                draggingHead = false; movingClip = null; moveDelta = 0f
                onScrubCancel(); invalidate(); return true
            }
        }
        gestures.onTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val frame = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> (playheadFrame - 1L).coerceAtLeast(0L)
            KeyEvent.KEYCODE_DPAD_RIGHT -> (playheadFrame + 1L).coerceAtMost(endFrame)
            KeyEvent.KEYCODE_MOVE_HOME -> 0L
            KeyEvent.KEYCODE_MOVE_END -> endFrame
            else -> return super.onKeyDown(keyCode, event)
        }
        (selectedTrack ?: tracks.firstOrNull())?.let { onSeek(it, frame) }
        return true
    }

    private val nodes = object : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(id: Int): AccessibilityNodeInfo? {
            if (id == View.NO_ID) return AccessibilityNodeInfo.obtain(this@TimelineView).also { info ->
                onInitializeAccessibilityNodeInfo(info)
                clips.indices.forEach { info.addChild(this@TimelineView, it + 1) }
                info.contentDescription = "Timeline playhead ${TimelineGeometry.timecode(playheadFrame)}"
            }
            val clip = clips.getOrNull(id - 1) ?: return null
            val bounds = Rect(); clipRect(clip).roundOut(bounds)
            return AccessibilityNodeInfo.obtain().also { info ->
                info.setSource(this@TimelineView, id); info.setParent(this@TimelineView)
                info.packageName = context.packageName; info.className = "android.widget.Button"
                info.contentDescription = "${clip.trackId} · ${clip.id} · ${TimelineGeometry.timecode(clip.startFrame)}"
                info.isEnabled = isEnabled; info.isClickable = true; info.isFocusable = true
                info.isAccessibilityFocused = focusedVirtualId == id
                info.setBoundsInParent(bounds)
                val location = IntArray(2); getLocationOnScreen(location); bounds.offset(location[0], location[1]); info.setBoundsInScreen(bounds)
                info.isVisibleToUser = isShown
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK)
                info.addAction(if (focusedVirtualId == id) AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS else AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            }
        }
        override fun performAction(id: Int, action: Int, arguments: Bundle?): Boolean {
            val clip = clips.getOrNull(id - 1) ?: return false
            if (!isEnabled) return false
            if (action == AccessibilityNodeInfo.ACTION_CLICK) { onSeek(clip.trackId, clip.startFrame); return true }
            if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS || action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
                focusedVirtualId = if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) id else View.NO_ID
                val event = AccessibilityEvent.obtain(if (focusedVirtualId == id) AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED else AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                event.setSource(this@TimelineView, id); event.packageName = context.packageName
                parent?.requestSendAccessibilityEvent(this@TimelineView, event)
                return true
            }
            return false
        }
    }
    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = nodes
}
