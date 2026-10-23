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
    private var clipNames = emptyMap<String, String>()
    private var tracks = emptyList<String>()
    private var laneIndices = emptyMap<String, Int>()
    private var clipIndicesById = emptyMap<String, Int>()
    private var clipIndex = TimelineClipIndex(emptyList())
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
    private var hoveredVirtualId = View.NO_ID
    var onSeek: (String, Long) -> Unit = { _, _ -> }
    var onScrub: (Long, Boolean) -> Unit = { _, _ -> }
    var onScrubCancel: () -> Unit = {}
    var onMove: (ClipSnapshot, Long) -> Unit = { _, _ -> }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = AppLanguage.text(R.string.message_9909c1b640)
    }

    fun setTimeline(values: List<ClipSnapshot>, selected: String?) {
        if (clips != values) {
            focusedVirtualId = View.NO_ID
            hoveredVirtualId = View.NO_ID
            clipIndex = TimelineClipIndex(values)
            clipIndicesById = values.withIndex().associate { it.value.id to it.index }
            clipNames = values.associate { it.id to (android.net.Uri.parse(it.mediaId).lastPathSegment?.substringAfterLast('/')?.takeLast(24) ?: it.id) }
            tracks = values.map { it.trackId }.distinct().sorted()
            laneIndices = tracks.withIndex().associate { it.value to it.index }
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
        clips = values
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
        val lane = laneIndices[clip.trackId] ?: return RectF()
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
        canvas.drawColor(StudioUi.surface)
        val visible = canvas.clipBounds
        paint.color = StudioUi.background
        canvas.drawRect(visible.left.toFloat(), 0f, visible.right.toFloat(), rulerHeight, paint)
        for (lane in tracks.indices) {
            paint.color = if (lane % 2 == 0) StudioUi.surface else StudioUi.background
            canvas.drawRect(visible.left.toFloat(), rulerHeight + lane * laneHeight, visible.right.toFloat(), rulerHeight + (lane+1) * laneHeight, paint)
            paint.color = StudioUi.border; paint.strokeWidth = density
            canvas.drawLine(visible.left.toFloat(), rulerHeight + (lane+1)*laneHeight, visible.right.toFloat(), rulerHeight + (lane+1)*laneHeight, paint)
        }
        if (clips.isEmpty()) {
            paint.color = StudioUi.muted; paint.textSize = 12f*density
            canvas.drawText(AppLanguage.text(R.string.timeline_empty), visible.left + 16*density, rulerHeight + 36*density, paint)
        }
        paint.color = StudioUi.muted
        paint.textSize = 10f * density
        val minTickFrames = (72.0 * density / pixelsPerFrame).coerceAtLeast(30.0)
        var tickFrames = 30L
        while (tickFrames < minTickFrames && tickFrames <= Long.MAX_VALUE / 2L) tickFrames *= 2L
        var tick = (frameAt(visible.left.toFloat()) / tickFrames) * tickFrames
        while (tick <= endFrame && xForFrame(tick) <= visible.right + 100f * density) {
            val x = xForFrame(tick)
            paint.color = StudioUi.muted
            canvas.drawLine(x, rulerHeight - 8 * density, x, rulerHeight, paint)
            canvas.drawText(TimelineGeometry.timecode(tick).substringBeforeLast(':'), x + 4 * density, 14 * density, paint)
            paint.color = StudioUi.border; paint.strokeWidth = density
            canvas.drawLine(x, rulerHeight, x, height.toFloat(), paint)
            for (minor in 1..3) {
                val minorX = x + (tickFrames * pixelsPerFrame * minor / 4).toFloat()
                canvas.drawLine(minorX, rulerHeight-4*density, minorX, rulerHeight, paint)
            }
            if (endFrame - tick < tickFrames) break
            tick += tickFrames
        }
        for ((lane, track) in tracks.withIndex()) {
            paint.color = StudioUi.muted
            canvas.drawText(AppLanguage.text(R.string.message_88e5aebc23, track), visible.left + 4f * density, rulerHeight + lane * laneHeight + 12 * density, paint)
        }
        // Expand by one pixel in frame space: very short clips draw at least 1 px.
        val padding = ceil(1.0 / pixelsPerFrame).toLong().coerceAtLeast(1L)
        val left = (frameAt(visible.left.toFloat()) - padding).coerceAtLeast(0L)
        val rightFrame = frameAt(visible.right.toFloat())
        val right = if (Long.MAX_VALUE - rightFrame < padding) Long.MAX_VALUE else rightFrame + padding
        val candidates = clipIndex.intersecting(left, right, movingClip?.let { clipIndicesById[it.id] })
        for (index in candidates) {
            val clip = clips[index]
            val rect = clipRect(clip)
            if (movingClip?.id == clip.id) rect.offset(moveDelta, 0f)
            if (rect.right < visible.left || rect.left > visible.right) continue
            paint.color = if (clip.id == selectedId) StudioUi.selection else StudioUi.raised
            canvas.drawRoundRect(rect, 7 * density, 7 * density, paint)
            if (clip.id == selectedId) {
                paint.color = StudioUi.accent; paint.style = Paint.Style.STROKE; paint.strokeWidth = density
                canvas.drawRoundRect(rect, 7 * density, 7 * density, paint); paint.style = Paint.Style.FILL
            }
            canvas.save()
            canvas.clipRect(rect)
            paint.color = if(clip.id == selectedId) StudioUi.accent else StudioUi.muted
            canvas.drawRect(rect.left, rect.top, rect.right, rect.top+3*density, paint)
            if (clip.id == selectedId && rect.width() > 30*density) {
                canvas.drawRoundRect(RectF(rect.left+3*density,rect.top+17*density,rect.left+5*density,rect.bottom-10*density),density,density,paint)
                canvas.drawRoundRect(RectF(rect.right-5*density,rect.top+17*density,rect.right-3*density,rect.bottom-10*density),density,density,paint)
            }
            paint.color = StudioUi.text; paint.textSize = 11f * density
            canvas.drawText((clipNames[clip.id] ?: clip.id), rect.left + 10 * density, rect.top + 19 * density, paint)
            paint.textSize = 9f * density
            canvas.drawText(TimelineGeometry.timecode(clip.durationFrames), rect.left + 10 * density, rect.top + 36 * density, paint)
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
                requestFocus()
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

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        if (!manager.isEnabled || !manager.isTouchExplorationEnabled) return super.dispatchHoverEvent(event)
        val next = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) View.NO_ID else
            clips.indexOfFirst { clipRect(it).contains(event.x, event.y) }.let { if (it < 0) View.NO_ID else it + 1 }
        if (next != hoveredVirtualId) {
            for ((id, type) in listOf(hoveredVirtualId to AccessibilityEvent.TYPE_VIEW_HOVER_EXIT,
                next to AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)) {
                if (id == View.NO_ID) continue
                val changed = AccessibilityEvent.obtain(type)
                changed.setSource(this, id); changed.packageName = context.packageName
                parent?.requestSendAccessibilityEvent(this, changed)
            }
            hoveredVirtualId = next
        }
        return next != View.NO_ID || event.actionMasked == MotionEvent.ACTION_HOVER_EXIT
    }

    private val nodes = object : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(id: Int): AccessibilityNodeInfo? {
            if (id == View.NO_ID) return AccessibilityNodeInfo.obtain(this@TimelineView).also { info ->
                onInitializeAccessibilityNodeInfo(info)
                clips.indices.forEach { info.addChild(this@TimelineView, it + 1) }
                info.contentDescription = AppLanguage.text(R.string.message_97a400a822, TimelineGeometry.timecode(playheadFrame))
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
                val visible = Rect()
                val localBounds = Rect(); clipRect(clip).roundOut(localBounds)
                info.isVisibleToUser = isShown && getLocalVisibleRect(visible) && Rect.intersects(visible, localBounds)
                info.isSelected = clip.id == selectedId
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
