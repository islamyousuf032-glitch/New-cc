package com.owncut.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.WindowInsets
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : Activity(), TextureView.SurfaceTextureListener {
    private var engine: EngineSession? = null
    private lateinit var preview: AspectRatioTextureView
    private var previewSurface: Surface? = null
    private lateinit var statusText: TextView
    private lateinit var engineStatusText: TextView
    private lateinit var mediaText: TextView
    private lateinit var timelineContainer: LinearLayout
    private var timelineView: TimelineView? = null
    private var timelineViewport: HorizontalScrollView? = null
    private lateinit var timelinePositionText: TextView
    private lateinit var followPlayheadButton: Button
    private var timelineClips = emptyList<ClipSnapshot>()
    private var timelineFrame = 0L
    private var timelineGapPosition = false
    private var timelineScrubbing = false
    private var followPlayhead = true
    private var restoreTimelineScrollX: Int? = null
    @Volatile private var previewSeeking = false
    @Volatile private var seekSerial = 0
    private lateinit var selectedClipText: TextView
    private var selectedClipId: String? = null
    private var hasUnsavedChanges = false
    private var editGeneration = 0L
    private var projectIoBusy = false
    private var selectedMediaLoading = false
    private var handingOffEngine = false
    @Volatile private var resumeAfterSelection = false
    private lateinit var projectPath: String
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mediaThread = HandlerThread("OwncutMediaPlayer").apply { start() }
    private val mediaHandler = Handler(mediaThread.looper)
    private val metadataExecutor = Executors.newSingleThreadScheduledExecutor()
    private val exportExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var exportCancellation: AtomicBoolean? = null
    private lateinit var cancelExportButton: Button
    private var pendingAutosave: ScheduledFuture<*>? = null
    private var metadataRequestSequence = 0
    @Volatile private var playerGeneration = 0
    private val playbackTicker = object : Runnable {
        override fun run() {
            val generation = playerGeneration
            mediaHandler.post {
                val current = player
                val tick = try {
                    if (current != null && !previewSeeking && current.isPlaying) {
                        val position = current.currentPosition
                        val end = previewEndMs.coerceAtMost(current.duration.toLong()).coerceAtLeast(0L).toInt()
                        if (position >= end) {
                            current.pause()
                            current.seekTo(end.toLong(), MediaPlayer.SEEK_CLOSEST)
                            end to true
                        } else position to false
                    } else null
                } catch (_: IllegalStateException) { null }
                if (tick != null) mainHandler.post {
                    if (generation == playerGeneration && player === current) {
                        if (timelineScrubbing || isSeeking || previewSeeking) return@post
                        val (position, reachedClipEnd) = tick
                        if (!isSeeking && !timelineScrubbing && !previewSeeking) {
                            seekBar.progress = position
                            previewPositionMs = position.toLong()
                            updateTimelinePlayhead(follow = true)
                        }
                        if (reachedClipEnd) {
                            advanceOrStopTimeline(generation, current)
                        } else {
                            if (firstVideoFrameRendered) {
                                statusText.text = "ভিডিও চলছে · ${formatTime(position.toLong())} / ${formatTime(durationMs)}"
                            }
                            mainHandler.postDelayed(this, 50L)
                        }
                    }
                }
            }
        }
    }
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    private lateinit var relinkButton: Button
    private var selectedUri: Uri? = null
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var playerPrepared = false
    @Volatile private var firstVideoFrameRendered = false
    private var firstFrameDiagnostic: Runnable? = null
    private var surfaceReady = false
    private var durationMs = 0L
    private var previewStartMs = 0L
    private var previewEndMs = 0L
    @Volatile private var previewPositionMs = 0L
    private var isSeeking = false

    private enum class ProjectIoOperation { SAVE, LOAD, RECOVER, EXPORT }

    private data class RetainedEditorState(
        val engine: EngineSession,
        val projectPath: String,
        val selectedUri: String?,
        val selectedClipId: String?,
        val hasUnsavedChanges: Boolean,
        val editGeneration: Long,
        val previewPositionMs: Long,
        val timelineFrame: Long,
        val timelineGapPosition: Boolean,
        val followPlayhead: Boolean,
        val timelineScrollX: Int,
    )

    private var projectIoOperation: ProjectIoOperation? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            initializeEditor(savedInstanceState)
        } catch (error: Exception) {
            showStartupFailure(error)
        } catch (error: LinkageError) {
            showStartupFailure(error)
        }
    }

    @Suppress("DEPRECATION")
    private fun initializeEditor(savedInstanceState: Bundle?) {
        val retained = lastNonConfigurationInstance as? RetainedEditorState
        if (retained != null) {
            engine = retained.engine
            projectPath = retained.projectPath
            selectedUri = retained.selectedUri?.let { Uri.parse(it) }
            selectedClipId = retained.selectedClipId
            hasUnsavedChanges = retained.hasUnsavedChanges
            editGeneration = retained.editGeneration
            previewPositionMs = retained.previewPositionMs
            timelineFrame = retained.timelineFrame
            timelineGapPosition = retained.timelineGapPosition
            followPlayhead = retained.followPlayhead
            restoreTimelineScrollX = retained.timelineScrollX
            engine?.clips()?.firstOrNull { it.id == selectedClipId }?.let {
                previewStartMs = it.sourceInMs
                previewEndMs = it.sourceOutMs
            }
        } else {
            engine = EngineSession()
            projectPath = File(filesDir, "active.owncut").absolutePath
        }
        buildWorkspace()
        preview.surfaceTextureListener = this
        if (timelineGapPosition) preview.alpha = 0f
        if (retained != null) {
            selectedUri?.let { readMetadataAndRegister(it, existingClipId = selectedClipId) }
            if (hasUnsavedChanges) scheduleAutosave()
        } else if (savedInstanceState != null) {
            statusText.text = "আগের session-এর জন্য Recover autosave চাপুন, অথবা media আবার নির্বাচন করুন।"
        }
        updateEngineStatus()
        refreshTimeline()
    }

    private fun showStartupFailure(error: Throwable) {
        android.util.Log.e("OwncutStartup", "Owncut could not create its initial workspace", error)
        try { engine?.close() } catch (_: Throwable) { }
        engine = null
        if (::preview.isInitialized) preview.surfaceTextureListener = null
        val surfaceToRelease = previewSurface
        previewSurface = null
        if (surfaceToRelease != null) mediaHandler.post { surfaceToRelease.release() }
        metadataExecutor.shutdownNow()
        mediaThread.quitSafely()
        val details = error.stackTraceToString().lineSequence().take(10).joinToString("\n")
        val message = TextView(this).apply {
            text = "Owncut চালু হতে পারেনি। এই পর্দার screenshot পাঠান।\n\n${error.javaClass.simpleName}: ${error.message ?: "কোনো বিবরণ নেই"}\n\n$details"
            textSize = 15f
            setTextColor(0xFFE5EEE8.toInt())
            setTextIsSelectable(true)
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF101713.toInt())
            addView(message)
        })
    }

    @Suppress("DEPRECATION")
    private fun buildWorkspace() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(0xFF101713.toInt())
        }
        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            setTextColor(0xFFE5EEE8.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(title, matchWrap())

        preview = AspectRatioTextureView(this).apply {
            // TextureView rejects background drawables/colors; keep its empty surface as the placeholder.
            contentDescription = "Video preview"
        }
        val previewParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240)).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        }
        root.addView(preview, previewParams)

        mediaText = TextView(this).apply {
            text = "একটি ভিডিও নির্বাচন করে preview শুরু করুন"
            textSize = 14f
            setTextColor(0xFFB5C6BB.toInt())
        }
        root.addView(mediaText, matchWrap())

        selectedClipText = TextView(this).apply {
            text = "Timeline · কোনো clip নির্বাচিত নয়"
            textSize = 13f
            setTextColor(0xFFE5EEE8.toInt())
            setPadding(0, dp(10), 0, dp(4))
        }
        root.addView(selectedClipText, matchWrap())
        val transportRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        timelinePositionText = TextView(this).apply {
            textSize = 12f; setTextColor(0xFFFF8991.toInt()); gravity = Gravity.CENTER_VERTICAL
            contentDescription = "Timeline position"
        }
        transportRow.addView(timelinePositionText, LinearLayout.LayoutParams(0, dp(48), 1f))
        followPlayheadButton = actionButton("প্লেহেড অনুসরণ") {
            followPlayhead = !followPlayhead; updateFollowButton()
            if (followPlayhead) revealPlayhead()
        }
        transportRow.addView(followPlayheadButton, wrapButtonParams())
        root.addView(transportRow, matchWrap())
        updateFollowButton()
        val timelineScroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
        }
        timelineContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        timelineScroll.addView(timelineContainer)
        root.addView(timelineScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(164)))

        seekBar = SeekBar(this).apply {
            isEnabled = false
            max = 1
            contentDescription = "Selected source clip seek bar; red timeline playhead shows project time"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser && !previewSeeking) {
                        previewPositionMs = progress.toLong().coerceIn(previewStartMs, previewEndMs.coerceAtLeast(previewStartMs))
                        updateTimelinePlayhead()
                        statusText.text = "Source: ${formatTime(progress.toLong())} / ${formatTime(durationMs)}"
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar) { isSeeking = true }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    isSeeking = false
                    if (playerPrepared && !previewSeeking) seekPreviewTo(bar.progress.toLong(), resumeIfPlaying = true)
                }
            })
        }
        root.addView(seekBar, matchWrap())

        val editHelpText = TextView(this).apply {
            text = "লাল রেখা timeline playhead। Ruler/clip-এ ট্যাপ করে seek; লাল মাথা ধরে scrub করুন। Clip সরাতে long-press; নিচের bar শুধু নির্বাচিত source-এর position। Gap-এ playback থামে।"
            textSize = 12f
            setTextColor(0xFF91A99A.toInt())
            setPadding(0, dp(2), 0, dp(6))
        }
        root.addView(editHelpText, matchWrap())

        val editScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val editControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        editControls.addView(actionButton("কাটুন (Split)") { splitAtPlayhead() }, wrapButtonParams())
        editControls.addView(actionButton("বাম ছাঁটুন") { trimAtPlayhead(leftEdge = true) }, wrapButtonParams())
        editControls.addView(actionButton("ডান ছাঁটুন") { trimAtPlayhead(leftEdge = false) }, wrapButtonParams())
        editControls.addView(actionButton("মুছুন") { deleteSelected(ripple = false) }, wrapButtonParams())
        editControls.addView(actionButton("গ্যাপ বাদ") { deleteSelected(ripple = true) }, wrapButtonParams())
        editScroll.addView(editControls)
        root.addView(editScroll, matchWrap())

        val projectControls = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val projectButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        projectButtons.addView(actionButton("Undo") { runHistory(undo = true) }, wrapButtonParams())
        projectButtons.addView(actionButton("Redo") { runHistory(undo = false) }, wrapButtonParams())
        projectButtons.addView(actionButton("Save") { saveProject() }, wrapButtonParams())
        projectButtons.addView(actionButton("Open saved") { confirmLoadProject() }, wrapButtonParams())
        projectButtons.addView(actionButton("Save project as…") { launchProjectSavePicker() }, wrapButtonParams())
        projectButtons.addView(actionButton("Open project…") { launchProjectOpenPicker() }, wrapButtonParams())
        projectButtons.addView(actionButton("Recover autosave") { confirmRecoverAutosave() }, wrapButtonParams())
        projectButtons.addView(actionButton("Export MP4") { launchExportPicker() }, wrapButtonParams())
        cancelExportButton = Button(this).apply {
            text = "Export বাতিল"
            isEnabled = false
            setOnClickListener {
                exportCancellation?.set(true)
                text = "বাতিল হচ্ছে…"
            }
        }
        projectButtons.addView(cancelExportButton, wrapButtonParams())
        projectControls.addView(projectButtons)
        root.addView(projectControls, matchWrap())

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val importButton = Button(this).apply {
            text = "ভিডিও নিন"
            setOnClickListener { launchVideoPicker() }
        }
        relinkButton = Button(this).apply {
            text = "মিডিয়া বদল"
            isEnabled = false
            contentDescription = "Selected clip-এর original media পুনরায় নির্বাচন করুন"
            setOnClickListener { launchRelinkPicker() }
        }
        playButton = Button(this).apply {
            text = "ভিডিও চালান"
            isEnabled = false
            contentDescription = "ভিডিও চালান বা বিরতি দিন"
            setOnClickListener { togglePlayback() }
        }
        controls.addView(importButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(relinkButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(playButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(controls, matchWrap())

        statusText = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF91A99A.toInt())
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(statusText, matchWrap())
        engineStatusText = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF91A99A.toInt())
            setPadding(0, dp(6), 0, 0)
        }
        root.addView(engineStatusText, matchWrap())
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val cutout = insets.displayCutout
                val left = maxOf(bars.left, cutout?.safeInsetLeft ?: 0)
                val top = maxOf(bars.top, cutout?.safeInsetTop ?: 0)
                val right = maxOf(bars.right, cutout?.safeInsetRight ?: 0)
                val bottom = maxOf(bars.bottom, cutout?.safeInsetBottom ?: 0)
                view.setPadding(dp(16) + left, dp(12) + top, dp(16) + right, dp(12) + bottom)
            } else {
                view.setPadding(
                    dp(16) + insets.systemWindowInsetLeft,
                    dp(12) + insets.systemWindowInsetTop,
                    dp(16) + insets.systemWindowInsetRight,
                    dp(12) + insets.systemWindowInsetBottom,
                )
            }
            insets
        }
        val workspaceScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(root, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        setContentView(workspaceScroll)
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener {
            if (projectIoBusy) Toast.makeText(this@MainActivity, "Project operation চলছে", Toast.LENGTH_SHORT).show()
            else action()
        }
    }

    private fun wrapButtonParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { rightMargin = dp(4) }

    private fun refreshTimeline() {
        if (!::timelineContainer.isInitialized || !::selectedClipText.isInitialized) return
        val clips = try { engine?.clips().orEmpty().sortedBy { it.startFrame } } catch (error: Exception) {
            statusText.text = "Timeline পড়া যায়নি: ${error.message}"; return
        }
        timelineClips = clips
        if (selectedClipId != null && clips.none { it.id == selectedClipId }) selectedClipId = null
        if (timelineView == null) {
            val canvas = TimelineView(this).apply {
                minimumWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(180))
                onSeek = { track, frame -> seekTimelineAt(track, frame) }
                onScrub = { frame, commit ->
                    if (!projectIoBusy) {
                        val startingScrub = !timelineScrubbing && !commit
                        timelineScrubbing = !commit
                        timelineFrame = frame; setPlayheadDisplay(frame)
                        if (commit) {
                            val track = timelineClips.firstOrNull { it.id == selectedClipId }?.trackId
                                ?: timelineClips.firstOrNull()?.trackId
                            if (track != null) seekTimelineAt(track, frame)
                        } else if (startingScrub) pauseForTimelineScrub()
                    }
                }
                onScrubCancel = { timelineScrubbing = false; updateTimelinePlayhead() }
                onMove = { clip, frame -> moveClipToFreeSlot(clip, clip.trackId, frame) }
            }
            timelineView = canvas
            val viewport = object : HorizontalScrollView(this) {
                override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
                    val intercepted = super.onInterceptTouchEvent(event)
                    if (intercepted && event.actionMasked == MotionEvent.ACTION_MOVE) {
                        followPlayhead = false; updateFollowButton()
                    }
                    return intercepted
                }
                override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
                    if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ||
                        action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                        followPlayhead = false; updateFollowButton()
                    }
                    return super.performAccessibilityAction(action, arguments)
                }
            }.apply {
                isHorizontalScrollBarEnabled = true
                addView(canvas, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            timelineViewport = viewport
            timelineContainer.addView(viewport, matchWrap())
        }
        timelineView?.apply { setTimeline(clips, selectedClipId); isEnabled = !projectIoBusy }
        val selected = clips.firstOrNull { it.id == selectedClipId }
        if (::relinkButton.isInitialized) relinkButton.isEnabled = selected != null && !projectIoBusy && !selectedMediaLoading
        selectedClipText.text = selected?.let {
            "Timeline · ${it.id} · source ${formatTime(it.sourceInMs)}–${formatTime(it.sourceOutMs)}"
        } ?: if (clips.isEmpty()) "ভিডিও import করলে timeline এখানে দেখা যাবে" else "Timeline · ${clips.size} clips · gap / কোনো clip নির্বাচিত নয়"
        updateTimelinePlayhead()
        restoreTimelineScrollX?.let { saved ->
            timelineViewport?.post { timelineViewport?.scrollTo(saved, 0) }
            restoreTimelineScrollX = null
        }
    }

    private fun updateFollowButton() {
        if (::followPlayheadButton.isInitialized)
            followPlayheadButton.text = if (followPlayhead) "অনুসরণ: চালু" else "অনুসরণ: বন্ধ"
    }

    private fun setPlayheadDisplay(frame: Long) {
        timelineFrame = frame.coerceIn(0L, timelineView?.endFrame ?: 0L)
        timelineView?.setPlayhead(timelineFrame)
        if (::timelinePositionText.isInitialized)
            timelinePositionText.text = "Timeline ${TimelineGeometry.timecode(timelineFrame)}"
    }

    private fun updateTimelinePlayhead(follow: Boolean = false) {
        if (timelineScrubbing) return
        val clip = timelineClips.firstOrNull { it.id == selectedClipId }
        val frame = if (clip != null && !timelineGapPosition)
            TimelineGeometry.frameForSource(clip, previewPositionMs) else timelineFrame
        setPlayheadDisplay(frame)
        if (follow && followPlayhead) revealPlayhead()
    }

    private fun revealPlayhead() {
        val view = timelineView ?: return
        val viewport = timelineViewport ?: return
        if (viewport.width <= 0) return
        val x = view.xForFrame(timelineFrame).toInt()
        if (x < viewport.scrollX + dp(12) || x > viewport.scrollX + viewport.width - dp(32))
            viewport.scrollTo((x - viewport.width / 3).coerceAtLeast(0), 0)
    }

    private fun pauseForTimelineScrub() {
        mainHandler.removeCallbacks(playbackTicker)
        val current = player
        val generation = playerGeneration
        mediaHandler.post {
            if (current != null && player === current && generation == playerGeneration) {
                try { if (current.isPlaying) current.pause() } catch (_: IllegalStateException) { }
            }
        }
        playButton.text = "ভিডিও চালান"
    }

    private fun seekTimelineAt(track: String, frame: Long) {
        if (projectIoBusy || previewSeeking) { blockWhileMediaLoads(); updateTimelinePlayhead(); return }
        try {
            val session = engine ?: return
            val target = session.resolvePlayhead(track, frame)
            timelineScrubbing = false
            followPlayhead = true; updateFollowButton()
            if (target == null) {
                metadataRequestSequence += 1; selectedMediaLoading = false
                selectedClipId = null; selectedUri = null
                previewStartMs = 0L; previewEndMs = 0L; previewPositionMs = 0L
                seekBar.max = 1; seekBar.progress = 0; durationMs = 0L
                releasePlayer(); preview.alpha = 0f; timelineGapPosition = true; timelineFrame = frame
                refreshTimeline()
                revealPlayhead()
                mediaText.text = "Timeline-এর এই স্থানে কোনো clip নেই"
                statusText.text = "Gap / timeline শেষ · clip-এ ট্যাপ করে preview করুন; project বদলায়নি"
                return
            }
            val clip = session.clips().first { it.id == target.clipId }
            timelineGapPosition = false
            if (clip.id == selectedClipId && playerPrepared && !selectedMediaLoading) {
                seekPreviewTo(target.sourcePositionMs)
            } else selectClip(clip, sourcePositionMs = target.sourcePositionMs)
            setPlayheadDisplay(target.timelineFrame)
            revealPlayhead()
        } catch (error: Exception) { showError(error); updateTimelinePlayhead() }
    }

    private fun seekPreviewTo(sourceMs: Long, resumeIfPlaying: Boolean = false, startAfterSeek: Boolean = false) {
        val current = player ?: return
        if (!playerPrepared || previewSeeking) return
        val target = sourceMs.coerceIn(previewStartMs, (previewEndMs - 1L).coerceAtLeast(previewStartMs))
        val serial = ++seekSerial
        val generation = playerGeneration
        previewSeeking = true; previewPositionMs = target
        seekBar.progress = target.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        updateTimelinePlayhead(); playButton.isEnabled = false
        mainHandler.removeCallbacks(playbackTicker)
        mediaHandler.post {
            if (player !== current || generation != playerGeneration) return@post
            try {
                val resume = startAfterSeek || (resumeIfPlaying && current.isPlaying)
                if (current.isPlaying) current.pause()
                current.setOnSeekCompleteListener { ready ->
                    if (player === ready && generation == playerGeneration && serial == seekSerial) {
                        ready.setOnSeekCompleteListener(null); previewSeeking = false
                        if (resume) ready.start()
                        mainHandler.post {
                            if (player === ready && generation == playerGeneration && serial == seekSerial) {
                                previewPositionMs = target
                                seekBar.progress = target.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                playButton.isEnabled = true
                                playButton.text = if (resume) "বিরতি" else "ভিডিও চালান"
                                updateTimelinePlayhead(follow = resume)
                                if (resume) mainHandler.post(playbackTicker)
                            }
                        }
                    }
                }
                current.seekTo(target, MediaPlayer.SEEK_CLOSEST)
            } catch (error: IllegalStateException) {
                mainHandler.post {
                    if (generation == playerGeneration) {
                        previewSeeking = false; playButton.isEnabled = playerPrepared
                        statusText.text = "Seek হয়নি: ${error.message}"
                    }
                }
            }
        }
    }

    private fun moveClipToFreeSlot(clip: ClipSnapshot, trackId: String, proposedStart: Long) {
        if (projectIoBusy || selectedMediaLoading || previewSeeking) return
        val session = engine ?: return
        try {
            val wasSelected = selectedClipId == clip.id
            val others = session.clips().filter { it.trackId == trackId && it.id != clip.id }
            val duration = clip.durationFrames
            fun isFree(start: Long): Boolean = others.none { other ->
                start < other.startFrame + other.durationFrames &&
                    other.startFrame < start + duration
            }
            val slots = buildList {
                add(proposedStart)
                add(0L)
                for (other in others) {
                    add((other.startFrame - duration).coerceAtLeast(0L))
                    add(other.startFrame + other.durationFrames)
                }
            }.distinct().filter(::isFree)
            val destination = slots.minByOrNull { kotlin.math.abs(it - proposedStart) }
            if (destination == clip.startFrame) return
            if (destination == null) {
                statusText.text = "এই track-এ clip রাখার ফাঁকা জায়গা নেই।"
                return
            }
            session.moveClip(clip.id, trackId, destination)
            selectedClipId = clip.id
            refreshTimeline()
            if (!wasSelected) session.clips().firstOrNull { it.id == clip.id }?.let { selectClip(it) }
            scheduleAutosave()
            updateEngineStatus()
            statusText.text = if (destination == proposedStart) "Timeline clip সরানো হয়েছে" else "Clip সংঘর্ষ এড়াতে নিকটতম ফাঁকা স্থানে বসানো হয়েছে"
        } catch (error: Exception) {
            showError(error)
        }
    }

    private fun selectClip(clip: ClipSnapshot, resumePlayback: Boolean = false, sourcePositionMs: Long = clip.sourceInMs) {
        if (projectIoBusy) return
        resumeAfterSelection = resumePlayback
        selectedClipId = clip.id
        releasePlayer()
        val uri = Uri.parse(clip.mediaId)
        selectedUri = uri
        previewStartMs = clip.sourceInMs
        previewEndMs = clip.sourceOutMs
        previewPositionMs = sourcePositionMs.coerceIn(clip.sourceInMs, clip.sourceOutMs - 1L)
        timelineGapPosition = false
        preview.alpha = 1f
        refreshTimeline()
        readMetadataAndRegister(uri, existingClipId = clip.id)
    }

    private fun currentSelectedClip(): ClipSnapshot? = engine?.clips()?.firstOrNull { it.id == selectedClipId }

    private fun advanceOrStopTimeline(expectedGeneration: Int, completedPlayer: MediaPlayer?) {
        if (expectedGeneration != playerGeneration || player !== completedPlayer) return
        val finishedClip = currentSelectedClip()
        val timelineEnd = finishedClip?.let { it.startFrame + it.durationFrames }
        val nextClip = if (finishedClip != null && timelineEnd != null) {
            engine?.clips()?.firstOrNull {
                it.trackId == finishedClip.trackId && it.startFrame == timelineEnd
            }
        } else null
        if (nextClip != null) {
            statusText.text = "পরের clip প্রস্তুত হচ্ছে…"
            selectClip(nextClip, resumePlayback = true)
        } else {
            mainHandler.removeCallbacks(playbackTicker)
            seekBar.progress = previewEndMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            previewPositionMs = previewEndMs
            updateTimelinePlayhead(follow = true)
            playButton.text = "ভিডিও চালান"
            statusText.text = "Timeline playback শেষ"
        }
    }

    private fun showFirstClip(session: EngineSession) {
        val first = session.clips().sortedBy { it.startFrame }.firstOrNull()
        selectedClipId = first?.id
        refreshTimeline()
        if (first != null) selectClip(first) else {
            selectedUri = null
            previewStartMs = 0L
            previewEndMs = 0L
            previewPositionMs = 0L
            releasePlayer()
            mediaText.text = "Timeline-এ কোনো clip নেই"
        }
    }

    private fun splitAtPlayhead() {
        if (blockWhileMediaLoads()) return
        val session = engine ?: return
        try {
            val clip = currentSelectedClip() ?: throw IllegalStateException("আগে timeline clip নির্বাচন করুন")
            val playheadMs = seekBar.progress.toLong()
            val sourceOffsetMs = playheadMs - clip.sourceInMs
            if (sourceOffsetMs <= 0L || playheadMs >= clip.sourceOutMs)
                throw IllegalArgumentException("Split point নির্বাচিত clip-এর ভেতরে হতে হবে")
            val frameOffset = (sourceOffsetMs * 30L + 500L) / 1000L
            val cutFrame = clip.startFrame + frameOffset
            val rightId = "${clip.id}-r-${System.nanoTime()}"
            session.splitClip(clip.id, cutFrame, rightId)
            selectedClipId = rightId
            refreshTimeline()
            scheduleAutosave()
            engineStatusText.text = session.status()
            engine?.clips()?.firstOrNull { it.id == rightId }?.let { selectClip(it) }
        } catch (error: Exception) { showError(error) }
    }

    private fun trimAtPlayhead(leftEdge: Boolean) {
        if (blockWhileMediaLoads()) return
        val session = engine ?: return
        try {
            val clip = currentSelectedClip() ?: throw IllegalStateException("আগে timeline clip নির্বাচন করুন")
            session.trimClip(clip.id, leftEdge, seekBar.progress.toLong())
            refreshTimeline()
            scheduleAutosave()
            engineStatusText.text = session.status()
            engine?.clips()?.firstOrNull { it.id == clip.id }?.let { selectClip(it) }
        } catch (error: Exception) { showError(error) }
    }

    private fun deleteSelected(ripple: Boolean) {
        if (blockWhileMediaLoads()) return
        val session = engine ?: return
        try {
            val clip = currentSelectedClip() ?: throw IllegalStateException("আগে timeline clip নির্বাচন করুন")
            session.deleteClip(clip.id, ripple)
            val remaining = engine?.clips().orEmpty().sortedBy { it.startFrame }
            selectedClipId = remaining.firstOrNull()?.id
            refreshTimeline()
            scheduleAutosave()
            engineStatusText.text = session.status()
            remaining.firstOrNull()?.let { selectClip(it) } ?: run {
                selectedUri = null
                previewStartMs = 0L
                previewEndMs = 0L
                previewPositionMs = 0L
                releasePlayer()
                mediaText.text = "Timeline-এ কোনো clip নেই"
            }
        } catch (error: Exception) { showError(error) }
    }

    private fun runHistory(undo: Boolean) {
        if (blockWhileMediaLoads()) return
        val session = engine ?: return
        try {
            val anchorFrame = timelineFrame
            val anchorTrack = timelineClips.firstOrNull { it.id == selectedClipId }?.trackId ?: "v1"
            val changed = if (undo) session.undo() else session.redo()
            if (!changed) {
                Toast.makeText(this, if (undo) "Undo history খালি" else "Redo history খালি", Toast.LENGTH_SHORT).show()
                return
            }
            val clips = session.clips().sortedBy { it.startFrame }
            if (clips.none { it.id == selectedClipId }) selectedClipId = clips.firstOrNull()?.id
            refreshTimeline()
            scheduleAutosave()
            engineStatusText.text = session.status()
            if (clips.isEmpty()) showFirstClip(session)
            else seekTimelineAt(anchorTrack, anchorFrame)
        } catch (error: Exception) { showError(error) }
    }

    private fun scheduleAutosave() {
        val session = engine ?: return
        hasUnsavedChanges = true
        editGeneration += 1
        val scheduledGeneration = editGeneration
        pendingAutosave?.cancel(false)
        pendingAutosave = metadataExecutor.schedule({
            try {
                session.saveAutosave(projectPath)
                mainHandler.post { if (!isDestroyed && !projectIoBusy && editGeneration == scheduledGeneration) statusText.text = "Autosave সংরক্ষিত হয়েছে" }
            } catch (error: Exception) {
                mainHandler.post { if (!isDestroyed) statusText.text = "Autosave ব্যর্থ: ${error.message}" }
            }
        }, 600L, TimeUnit.MILLISECONDS)
    }

    private fun saveProject() {
        if (blockWhileMediaLoads()) return
        AlertDialog.Builder(this)
            .setTitle("Project save করবেন?")
            .setMessage("আগের saved project প্রতিস্থাপিত হবে। সফল save-এর পরে autosave snapshot-ও clear হবে।")
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("Save") { _, _ ->
                if (projectIoBusy || selectedMediaLoading) return@setPositiveButton
                val session = engine ?: return@setPositiveButton
                pendingAutosave?.cancel(false)
                projectIoBusy = true
                projectIoOperation = ProjectIoOperation.SAVE
                val generationAtSave = editGeneration
                statusText.text = "Project save হচ্ছে…"
                metadataExecutor.execute {
                    try {
                        session.saveProject(projectPath)
                        session.clearAutosave(projectPath)
                        mainHandler.post {
                            if (!isDestroyed) {
                                projectIoBusy = false
                                projectIoOperation = null
                                if (editGeneration == generationAtSave) hasUnsavedChanges = false
                                statusText.text = "Project save সম্পন্ন"
                            }
                        }
                    } catch (error: Exception) {
                        mainHandler.post {
                            if (!isDestroyed) {
                                projectIoBusy = false
                                projectIoOperation = null
                                statusText.text = "Project save ব্যর্থ: ${error.message}"
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun confirmLoadProject() {
        if (blockWhileMediaLoads()) return
        AlertDialog.Builder(this)
            .setTitle("Saved project খুলবেন?")
            .setMessage("বর্তমান session বদলে যাবে। আগে বর্তমান কাজ autosave করে তারপর saved project load হবে।")
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("Open") { _, _ ->
                if (projectIoBusy || selectedMediaLoading) return@setPositiveButton
                val session = engine ?: return@setPositiveButton
                pendingAutosave?.cancel(false)
                projectIoBusy = true
                projectIoOperation = ProjectIoOperation.LOAD
                statusText.text = "Project খোলা হচ্ছে…"
                metadataExecutor.execute {
                    try {
                        session.saveAutosave(projectPath)
                        session.loadProject(projectPath)
                        mainHandler.post {
                            if (!isDestroyed) {
                                projectIoBusy = false
                                projectIoOperation = null
                                hasUnsavedChanges = false
                                updateEngineStatus()
                                showFirstClip(session)
                                statusText.text = "Saved project খোলা হয়েছে"
                            }
                        }
                    } catch (error: Exception) {
                        mainHandler.post {
                            if (!isDestroyed) {
                                projectIoBusy = false
                                projectIoOperation = null
                                statusText.text = "Project খোলা যায়নি: ${error.message}"
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun confirmRecoverAutosave() {
        if (blockWhileMediaLoads()) return
        AlertDialog.Builder(this)
            .setTitle("Autosave recover করবেন?")
            .setMessage("Recovery snapshot বর্তমান in-memory project-কে প্রতিস্থাপন করবে। আগে আলাদা project save করুন যদি বর্তমান কাজ রাখতে চান।")
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("Recover") { _, _ ->
                if (projectIoBusy || selectedMediaLoading) return@setPositiveButton
                val session = engine ?: return@setPositiveButton
                pendingAutosave?.cancel(false)
                projectIoBusy = true
                projectIoOperation = ProjectIoOperation.RECOVER
                statusText.text = "Recovery snapshot পরীক্ষা হচ্ছে…"
                metadataExecutor.execute {
                    try {
                        val recovered = session.loadAutosave(projectPath)
                        mainHandler.post {
                            if (!isDestroyed) {
                                projectIoBusy = false
                                projectIoOperation = null
                                if (recovered) {
                                    hasUnsavedChanges = false
                                    updateEngineStatus()
                                    showFirstClip(session)
                                    statusText.text = "Autosave recover হয়েছে"
                                } else statusText.text = "Recovery snapshot নেই"
                            }
                        }
                    } catch (error: Exception) {
                        mainHandler.post {
                            if (!isDestroyed) {
                                projectIoBusy = false
                                projectIoOperation = null
                                statusText.text = "Autosave recover ব্যর্থ: ${error.message}"
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun blockWhileMediaLoads(): Boolean {
        if (previewSeeking) {
            Toast.makeText(this, "Seek শেষ হতে অপেক্ষা করুন", Toast.LENGTH_SHORT).show()
            return true
        }
        if (projectIoBusy) {
            Toast.makeText(this, "চলমান save/load/export শেষ করুন বা export বাতিল করুন", Toast.LENGTH_SHORT).show()
            return true
        }
        if (!selectedMediaLoading) return false
        Toast.makeText(this, "Selected media প্রস্তুত হচ্ছে", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun showError(error: Exception) {
        Toast.makeText(this, error.message ?: "Edit operation ব্যর্থ", Toast.LENGTH_LONG).show()
    }

    private fun frameToMs(frames: Long): Long = Math.addExact(Math.multiplyExact(frames.coerceAtLeast(0L) / 30L, 1000L), (frames.coerceAtLeast(0L) % 30L * 1000L) / 30L)

    private fun launchExportPicker() {
        if (projectIoBusy || blockWhileMediaLoads()) return
        try {
            TimelineRemuxExporter.makePlan(engine?.clips().orEmpty())
        } catch (error: Exception) {
            showError(error)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startTimelineExport(null)
        } else {
            launchExportDocumentPicker()
        }
    }

    private fun launchExportDocumentPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/mp4"
            putExtra(Intent.EXTRA_TITLE, "Owncut-edited.mp4")
        }
        startActivityForResult(intent, REQUEST_EXPORT_VIDEO)
    }

    private fun startTimelineExport(destination: Uri?) {
        if (projectIoBusy) return
        val plan = try { TimelineRemuxExporter.makePlan(engine?.clips().orEmpty()) } catch (error: Exception) {
            showError(error)
            return
        }
        if (plan.omittedGapFrames > 0L) {
            val omittedGapMs = frameToMs(plan.omittedGapFrames)
            AlertDialog.Builder(this)
                .setTitle("Timeline-এ ফাঁকা অংশ আছে")
                .setMessage("${plan.omittedGapFrames} frame (${omittedGapMs} ms) ফাঁকা অংশ বাদ দিয়ে clips পাশাপাশি জোড়া দিয়ে export হবে। Timeline project বদলাবে না। চালিয়ে যাবেন?")
                .setNegativeButton("বাতিল") { _, _ -> destination?.let { deleteFailedExportDocument(it) } }
                .setOnCancelListener { destination?.let { deleteFailedExportDocument(it) } }
                .setPositiveButton("Gap বাদ দিয়ে export") { _, _ -> beginTimelineExport(destination, plan) }
                .show()
        } else {
            beginTimelineExport(destination, plan)
        }
    }

    private fun beginTimelineExport(destination: Uri?, plan: TimelineRemuxExporter.Plan) {
        if (projectIoBusy) return
        val cancellation = AtomicBoolean(false)
        exportCancellation = cancellation
        projectIoBusy = true
        projectIoOperation = ProjectIoOperation.EXPORT
        timelineView?.isEnabled = false
        cancelExportButton.text = "Export বাতিল"
        cancelExportButton.isEnabled = true
        statusText.text = "Export প্রস্তুত হচ্ছে…"
        exportExecutor.execute {
            val temporary = File(cacheDir, "owncut-export-${System.nanoTime()}.mp4")
            var resultMessage = if (plan.omittedGapFrames > 0L) {
                "Export সম্পন্ন · ফাঁকা timeline gap বাদ দেওয়া হয়েছে"
            } else {
                if (destination == null) "Export সম্পন্ন · Gallery / Movies/Owncut-এ সংরক্ষিত"
                else "Export সম্পন্ন · MP4 Documents-এ সংরক্ষিত"
            }
            try {
                TimelineRenderExporter.export(this@MainActivity, plan, temporary, cancellation) { message ->
                    mainHandler.post { if (!isDestroyed) statusText.text = message }
                }
                if (destination == null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        publishExportToGallery(temporary, cancellation)
                    } else {
                        error("Gallery export-এর জন্য Android 10 বা নতুন সংস্করণ দরকার")
                    }
                }
                else copyExportToDocument(temporary, destination, cancellation)
            } catch (cancel: CancellationException) {
                val cleaned = destination == null || deleteFailedExportDocument(destination)
                resultMessage = if (destination == null) (cancel.message ?: "Export বাতিল হয়েছে")
                    else if (cleaned) "Export বাতিল হয়েছে; নতুন output রাখা হয়নি"
                    else "Export বাতিল; provider খালি/অসম্পূর্ণ document সরাতে পারেনি"
            } catch (error: Exception) {
                val cleaned = destination == null || deleteFailedExportDocument(destination)
                resultMessage = "Export ব্যর্থ: ${error.message ?: error.javaClass.simpleName}" +
                    if (cleaned) "" else " · অসম্পূর্ণ document সরানো যায়নি"
            } finally {
                temporary.delete()
                exportCancellation = null
                mainHandler.post {
                    if (!isDestroyed) {
                        projectIoBusy = false
                        projectIoOperation = null
                        cancelExportButton.isEnabled = false
                        timelineView?.isEnabled = true
                        cancelExportButton.text = "Export বাতিল"
                        statusText.text = resultMessage
                    }
                }
            }
        }
    }

    /** Publish only a validated MP4; keep the Gallery row hidden until the copy closes. */
    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    private fun publishExportToGallery(file: File, cancellation: AtomicBoolean) {
        ExportPublication.publish(file, cancellation) { GalleryPendingOutput.create(this) }
    }

    private fun deleteFailedExportDocument(destination: Uri): Boolean = try {
        DocumentsContract.deleteDocument(contentResolver, destination)
    } catch (_: Exception) {
        false
    }

    private fun copyExportToDocument(file: File, destination: Uri, cancellation: AtomicBoolean) {
        if (cancellation.get()) throw CancellationException("Export বাতিল করা হয়েছে")
        val output = contentResolver.openOutputStream(destination, "wt")
            ?: throw IllegalStateException("নির্বাচিত document-এ লেখা যাচ্ছে না")
        output.buffered().use { target ->
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    if (cancellation.get() || Thread.currentThread().isInterrupted)
                        throw CancellationException("Export বাতিল করা হয়েছে")
                    val count = input.read(buffer)
                    if (count < 0) break
                    target.write(buffer, 0, count)
                }
                target.flush()
            }
        }
    }

    private fun launchRelinkPicker() {
        if (projectIoBusy || blockWhileMediaLoads()) return
        val clipId = selectedClipId ?: run {
            Toast.makeText(this, "আগে timeline clip নির্বাচন করুন", Toast.LENGTH_SHORT).show()
            return
        }
        if (engine?.clips()?.any { it.id == clipId } != true) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_RELINK_MEDIA)
    }

    private fun launchProjectSavePicker() {
        if (projectIoBusy || blockWhileMediaLoads()) return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "Owncut-project.owncut")
        }
        startActivityForResult(intent, REQUEST_PROJECT_SAVE)
    }

    private fun launchProjectOpenPicker() {
        if (projectIoBusy || blockWhileMediaLoads()) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // SAF providers do not consistently register the .owncut extension MIME type.
        }
        startActivityForResult(intent, REQUEST_PROJECT_OPEN)
    }

    private fun saveProjectToDocument(uri: Uri) {
        val session = engine ?: return
        pendingAutosave?.cancel(false)
        projectIoBusy = true
        projectIoOperation = ProjectIoOperation.SAVE
        val generationAtSave = editGeneration
        statusText.text = "Project ফাইলে লেখা হচ্ছে…"
        metadataExecutor.execute {
            val temporary = File(cacheDir, "project-export-${System.nanoTime()}.owncut")
            try {
                session.saveProject(temporary.absolutePath)
                if (temporary.length() > ProjectDocumentIo.MAX_PROJECT_BYTES) throw IllegalStateException("Project ফাইল 96 MiB সীমা ছাড়িয়েছে")
                contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    temporary.inputStream().buffered().use { input -> ProjectDocumentIo.copy(input, output) }
                } ?: throw IllegalStateException("নির্বাচিত document-এ লেখা যাচ্ছে না")
                session.clearAutosave(projectPath)
                temporary.delete()
                mainHandler.post {
                    if (!isDestroyed) {
                        projectIoBusy = false
                        projectIoOperation = null
                        if (editGeneration == generationAtSave) hasUnsavedChanges = false
                        statusText.text = "Project Documents-এ save হয়েছে"
                    }
                }
            } catch (error: Exception) {
                temporary.delete()
                mainHandler.post {
                    if (!isDestroyed) {
                        projectIoBusy = false
                        projectIoOperation = null
                        statusText.text = "Project ফাইলে save ব্যর্থ: ${error.message}"
                    }
                }
            }
        }
    }

    private fun confirmOpenProjectDocument(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Project খুলবেন?")
            .setMessage("বর্তমান editor session বদলে যাবে। আগে unsaved পরিবর্তন autosave করা হবে।")
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("Open") { _, _ ->
                if (projectIoBusy || selectedMediaLoading) return@setPositiveButton
                val session = engine ?: return@setPositiveButton
                pendingAutosave?.cancel(false)
                projectIoBusy = true
                projectIoOperation = ProjectIoOperation.LOAD
                statusText.text = "Project document পড়া হচ্ছে…"
                metadataExecutor.execute {
                    val temporary = File(cacheDir, "project-import-${System.nanoTime()}.owncut")
                    try {
                        session.saveAutosave(projectPath)
                        contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                            temporary.outputStream().buffered().use { output -> ProjectDocumentIo.copy(input, output) }
                        } ?: throw IllegalStateException("নির্বাচিত document পড়া যাচ্ছে না")
                        session.loadProject(temporary.absolutePath)
                        session.clearAutosave(projectPath)
                        temporary.delete()
                        mainHandler.post {
                            if (!isDestroyed) {
                                projectIoBusy = false
                                projectIoOperation = null
                                hasUnsavedChanges = false
                                updateEngineStatus()
                                showFirstClip(session)
                                statusText.text = "Project document খোলা হয়েছে"
                            }
                        }
                    } catch (error: Exception) {
                        temporary.delete()
                        mainHandler.post {
                            if (!isDestroyed) {
                                projectIoBusy = false
                                projectIoOperation = null
                                statusText.text = "Project খোলা যায়নি: ${error.message}"
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun launchVideoPicker() {
        if (projectIoBusy) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_VIDEO)
    }

    @Deprecated("Uses platform Activity result API to avoid adding an AndroidX runtime dependency in the bootstrap.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val resultData = data ?: return
        val uri = resultData.data ?: return
        when (requestCode) {
            REQUEST_VIDEO -> {
                val takeGrant = resultData.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
                readMetadataAndRegister(uri, takeGrant)
            }
            REQUEST_RELINK_MEDIA -> {
                val takeGrant = resultData.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
                selectedClipId?.let { clipId -> readMetadataAndRegister(uri, takeGrant, relinkClipId = clipId) }
            }
            REQUEST_PROJECT_SAVE -> saveProjectToDocument(uri)
            REQUEST_PROJECT_OPEN -> confirmOpenProjectDocument(uri)
            REQUEST_EXPORT_VIDEO -> startTimelineExport(uri)
        }
    }

    private fun readMetadataAndRegister(
        uri: Uri,
        takePersistableGrant: Boolean = false,
        existingClipId: String? = null,
        relinkClipId: String? = null,
    ) {
        if (projectIoBusy) return
        val request = ++metadataRequestSequence
        selectedMediaLoading = true
        seekBar.isEnabled = false
        statusText.text = "ভিডিওর metadata পড়া হচ্ছে…"
        metadataExecutor.execute {
            var duration = 0L
            var displayWidth = 0
            var displayHeight = 0
            var persistedGrant = false
            var failure: Exception? = null
            val retriever = MediaMetadataRetriever()
            try {
                if (takePersistableGrant) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: SecurityException) {
                        // Some document providers grant only temporary access; the warning is shown below.
                    }
                }
                persistedGrant = contentResolver.persistedUriPermissions.any {
                    it.uri == uri && it.isReadPermission
                }
                retriever.setDataSource(this, uri)
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: throw IllegalArgumentException("ভিডিওর duration পড়া যায়নি")
                if (duration <= 0L) throw IllegalArgumentException("ভিডিওতে বৈধ duration নেই")

                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                if (width <= 0 || height <= 0) throw IllegalArgumentException("ভিডিও track-এর dimensions পাওয়া যায়নি")
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                displayWidth = if (rotation == 90 || rotation == 270) height else width
                displayHeight = if (rotation == 90 || rotation == 270) width else height
            } catch (error: Exception) {
                failure = error
            } finally {
                try { retriever.release() } catch (_: RuntimeException) { }
            }

            mainHandler.post {
                if (request != metadataRequestSequence || isFinishing || isDestroyed) return@post
                val error = failure
                if (error != null) {
                    selectedMediaLoading = false
                    mediaText.text = "এই ভিডিও খোলা যায়নি: ${error.message ?: "unsupported media"}"
                    statusText.text = "সমর্থিত SDR ভিডিও নির্বাচন করে আবার চেষ্টা করুন।"
                    if (existingClipId != null) {
                        resumeAfterSelection = false
                        previewEndMs = 0L
                        playButton.isEnabled = false
                        releasePlayer()
                    }
                    return@post
                }

                try {
                    selectedMediaLoading = false
                    val session = engine ?: throw IllegalStateException("Editor engine is unavailable")
                    val existing = existingClipId?.let { id -> session.clips().firstOrNull { it.id == id } }
                    if (existingClipId != null && existing == null) throw IllegalStateException("Preview-র নির্বাচিত clip আর নেই")
                    if (existing != null && existing.sourceOutMs > duration)
                        throw IllegalArgumentException("Source video বদলেছে বা ছোট হয়েছে; Media বদল দিয়ে relink করুন")
                    val relinkTarget = relinkClipId?.let { id -> session.clips().firstOrNull { it.id == id } }
                    if (relinkClipId != null && relinkTarget == null) throw IllegalStateException("যে clip-টি relink করতে চেয়েছেন সেটি আর নেই")
                    if (relinkTarget != null) session.relinkClipMedia(relinkTarget.id, uri.toString(), duration)
                    val clipId = relinkTarget?.id ?: existing?.id ?: session.addMediaClip(uri.toString(), duration)
                    val addedNewClip = existing == null && relinkTarget == null
                    selectedClipId = clipId
                    previewStartMs = relinkTarget?.sourceInMs ?: existing?.sourceInMs ?: 0L
                    previewEndMs = relinkTarget?.sourceOutMs ?: existing?.sourceOutMs ?: duration
                    if (addedNewClip) previewPositionMs = 0L
                    previewPositionMs = previewPositionMs.coerceIn(previewStartMs,
                        (previewEndMs - 1L).coerceAtLeast(previewStartMs))
                    timelineGapPosition = false; preview.alpha = 1f
                    selectedUri = uri
                    durationMs = duration
                    preview.setVideoSize(displayWidth, displayHeight)
                    seekBar.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
                    seekBar.progress = previewPositionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val grantInfo = if (persistedGrant) {
                        "SAF read access সংরক্ষিত; Original media অপরিবর্তিত থাকবে।"
                    } else {
                        "সতর্কতা: স্থায়ী access পাওয়া যায়নি; পরে media relink লাগতে পারে।"
                    }
                    mediaText.text = "${uri.lastPathSegment ?: "Video"} · ${displayWidth}×${displayHeight} · ${formatTime(duration)} · $clipId\n$grantInfo"
                    if (surfaceReady) preparePlayer(uri)
                    updateEngineStatus()
                    refreshTimeline()
                    if (addedNewClip || relinkTarget != null) scheduleAutosave()
                } catch (error: Exception) {
                    if (relinkClipId != null) statusText.text = "Media relink হয়নি: ${error.message}"
                    else mediaText.text = "ভিডিও timeline-এ যোগ হয়নি: ${error.message}"
                }
            }
        }
    }

    private fun preparePlayer(uri: Uri) {
        val outputSurface = previewSurface ?: run {
            statusText.text = "Preview surface প্রস্তুত নয়; ভিডিও screen খুলতে অপেক্ষা করুন।"
            return
        }
        releasePlayer()
        val generation = playerGeneration
        statusText.text = "ভিডিও decode হচ্ছে…"
        playButton.isEnabled = false
        mediaHandler.post {
            if (generation != playerGeneration) return@post
            val candidate = MediaPlayer()
            if (generation != playerGeneration) {
                candidate.release()
                return@post
            }
            player = candidate
            if (generation != playerGeneration) {
                player = null
                candidate.release()
                return@post
            }
            try {
                candidate.setSurface(outputSurface)
                candidate.setOnPreparedListener { ready ->
                    if (generation != playerGeneration || player !== ready) return@setOnPreparedListener
                    val readyDuration = ready.duration
                    val startPosition = previewPositionMs.coerceIn(previewStartMs,
                        (minOf(previewEndMs, readyDuration.toLong()) - 1L).coerceAtLeast(previewStartMs))
                    val shouldResume = resumeAfterSelection
                    resumeAfterSelection = false
                    fun positioned() {
                        if (generation != playerGeneration || player !== ready) return
                        try {
                            ready.setOnSeekCompleteListener(null)
                            previewSeeking = false; playerPrepared = true
                            if (shouldResume) ready.start()
                            mainHandler.post {
                                if (generation == playerGeneration && player === ready) {
                                    durationMs = readyDuration.toLong()
                                    previewPositionMs = startPosition
                                    seekBar.isEnabled = true
                                    seekBar.max = readyDuration.coerceAtLeast(1)
                                    seekBar.progress = startPosition.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                    updateTimelinePlayhead(follow = shouldResume)
                                    playButton.isEnabled = true
                                    if (shouldResume) {
                                        playButton.text = "বিরতি"
                                        scheduleFirstFrameDiagnostic(generation, ready)
                                        mainHandler.removeCallbacks(playbackTicker)
                                        mainHandler.post(playbackTicker)
                                    } else statusText.text = "Preview প্রস্তুত · timeline-এ লাল playhead / ভিডিও চালান চাপুন"
                                }
                            }
                        } catch (error: IllegalStateException) {
                            playerPrepared = false; previewSeeking = false
                            mainHandler.post { if (generation == playerGeneration) statusText.text = "Preview seek/start হয়নি: ${error.message}" }
                        }
                    }
                    try {
                        if (startPosition > 0L) {
                            previewSeeking = true
                            ready.setOnSeekCompleteListener { positioned() }
                            ready.seekTo(startPosition, MediaPlayer.SEEK_CLOSEST)
                        } else positioned()
                    } catch (error: IllegalStateException) {
                        previewSeeking = false
                        mainHandler.post { if (generation == playerGeneration) statusText.text = "Preview seek হয়নি: ${error.message}" }
                    }
                }
                candidate.setOnCompletionListener { completed ->
                    if (generation == playerGeneration && player === completed) {
                        mainHandler.post { advanceOrStopTimeline(generation, completed) }
                    }
                }
                candidate.setOnErrorListener { failed, what, extra ->
                    if (generation == playerGeneration && player === failed) {
                        resumeAfterSelection = false
                        player = null
                        playerPrepared = false
                        previewSeeking = false
                        failed.release()
                        mainHandler.post {
                            clearFirstFrameDiagnostic()
                            mainHandler.removeCallbacks(playbackTicker)
                            playButton.isEnabled = false
                            playButton.text = "ভিডিও চালান"
                            statusText.text = "ভিডিও decode হয়নি ($what/$extra)। 1080p SDR ফাইল দিয়ে চেষ্টা করুন; 4K/HDR/codec support ফোনভেদে আলাদা।"
                        }
                    }
                    true
                }
                candidate.setDataSource(this, uri)
                candidate.prepareAsync()
            } catch (error: Exception) {
                if (generation == playerGeneration) resumeAfterSelection = false
                if (player === candidate) {
                    player = null
                    playerPrepared = false
                    previewSeeking = false
                }
                candidate.release()
                mainHandler.post {
                    if (generation == playerGeneration) statusText.text = "Preview চালু হয়নি: ${error.message ?: "decoder error"}"
                }
            }
        }
    }

    private fun scheduleFirstFrameDiagnostic(generation: Int, expectedPlayer: MediaPlayer) {
        clearFirstFrameDiagnostic()
        val check = Runnable {
            if (generation == playerGeneration && player === expectedPlayer && !firstVideoFrameRendered) {
                statusText.text = "ভিডিও চালু, কিন্তু frame আসেনি। 1080p SDR ভিডিও দিয়ে চেষ্টা করুন; 4K/HDR/codec support ফোনভেদে আলাদা।"
            }
        }
        firstFrameDiagnostic = check
        mainHandler.postDelayed(check, 2_500L)
    }

    private fun clearFirstFrameDiagnostic() {
        val pending = firstFrameDiagnostic
        if (pending != null) mainHandler.removeCallbacks(pending)
        firstFrameDiagnostic = null
    }

    private fun togglePlayback() {
        val current = player
        if (!playerPrepared || current == null || previewSeeking) {
            statusText.text = "ভিডিও এখনো প্রস্তুত নয়—clip নির্বাচন করে একটু অপেক্ষা করুন।"
            return
        }
        val generation = playerGeneration
        mediaHandler.post {
            if (generation != playerGeneration || player !== current) return@post
            try {
                if (current.isPlaying) {
                    current.pause()
                    mainHandler.post {
                        clearFirstFrameDiagnostic()
                        mainHandler.removeCallbacks(playbackTicker)
                        playButton.text = "ভিডিও চালান"
                        statusText.text = "বিরতি"
                    }
                } else {
                    val position = current.currentPosition.toLong()
                    if (position < previewStartMs || position >= previewEndMs) {
                        mainHandler.post {
                            if (generation == playerGeneration && player === current)
                                seekPreviewTo(previewStartMs, startAfterSeek = true)
                        }
                        return@post
                    }
                    current.start()
                    mainHandler.post {
                        playButton.text = "বিরতি"
                        if (!firstVideoFrameRendered) {
                            statusText.text = "ভিডিও frame-এর অপেক্ষা করছি…"
                            scheduleFirstFrameDiagnostic(generation, current)
                        }
                        mainHandler.removeCallbacks(playbackTicker)
                        mainHandler.post(playbackTicker)
                    }
                }
            } catch (error: IllegalStateException) {
                mainHandler.post { statusText.text = "Playback state error: ${error.message}" }
            }
        }
    }

    private fun releasePlayer() {
        mainHandler.removeCallbacks(playbackTicker)
        clearFirstFrameDiagnostic()
        firstVideoFrameRendered = false
        previewSeeking = false
        seekSerial += 1
        val old = player
        player = null
        playerPrepared = false
        isSeeking = false; timelineScrubbing = false
        if (::seekBar.isInitialized) seekBar.isEnabled = false
        playerGeneration += 1
        playButtonOrNull()?.let { it.isEnabled = false; it.text = "ভিডিও চালান" }
        if (old != null) mediaHandler.post {
            try { old.setSurface(null) } catch (_: IllegalStateException) { }
            try { old.release() } catch (_: IllegalStateException) { }
        }
    }

    private fun playButtonOrNull(): Button? = if (::playButton.isInitialized) playButton else null

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        try {
            previewSurface = Surface(texture)
            surfaceReady = true
            if (!selectedMediaLoading) selectedUri?.let(::preparePlayer)
        } catch (error: RuntimeException) {
            showStartupFailure(error)
        }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        val outputSurface = previewSurface ?: return
        val current = player ?: return
        val generation = playerGeneration
        mediaHandler.post {
            if (generation == playerGeneration && player === current) {
                try {
                    current.setSurface(outputSurface)
                } catch (_: IllegalStateException) {
                    mainHandler.post {
                        if (generation == playerGeneration && player === current) {
                            statusText.text = "Preview surface পুনরায় যুক্ত করা যায়নি; clip আবার নির্বাচন করুন।"
                        }
                    }
                }
            }
        }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        surfaceReady = false
        releasePlayer()
        val oldSurface = previewSurface
        previewSurface = null
        mediaHandler.post {
            oldSurface?.release()
            texture.release()
        }
        // Keep the texture alive until MediaPlayer has detached on its own thread.
        return false
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
        if (!firstVideoFrameRendered && playerPrepared && player != null) {
            firstVideoFrameRendered = true
            clearFirstFrameDiagnostic()
            statusText.text = "ভিডিও frame preview-তে এসেছে"
        }
    }

    override fun onStart() {
        super.onStart()
        if (surfaceReady && !selectedMediaLoading && player == null) selectedUri?.let(::preparePlayer)
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    @Suppress("DEPRECATION") // Retain the native session without adding a ViewModel dependency to this bootstrap.
    override fun onRetainNonConfigurationInstance(): Any? {
        if (projectIoBusy && projectIoOperation != ProjectIoOperation.EXPORT) return null
        // Render uses an immutable plan: cancel the old job but retain the editable native session.
        pendingAutosave?.cancel(false)
        val currentEngine = engine ?: return null
        val retained = RetainedEditorState(
            engine = currentEngine,
            projectPath = projectPath,
            selectedUri = selectedUri?.toString(),
            selectedClipId = selectedClipId,
            hasUnsavedChanges = hasUnsavedChanges,
            editGeneration = editGeneration,
            previewPositionMs = previewPositionMs,
            timelineFrame = timelineFrame,
            timelineGapPosition = timelineGapPosition,
            followPlayhead = followPlayhead,
            timelineScrollX = timelineViewport?.scrollX ?: 0,
        )
        handingOffEngine = true
        engine = null
        return retained
    }

    override fun onDestroy() {
        metadataRequestSequence += 1
        if (!handingOffEngine) pendingAutosave?.cancel(false)
        if (::preview.isInitialized) preview.surfaceTextureListener = null
        releasePlayer()
        val surfaceToRelease = previewSurface
        previewSurface = null
        if (surfaceToRelease != null) mediaHandler.post { surfaceToRelease.release() }
        mediaThread.quitSafely()
        val closingSession = engine
        val operationAtShutdown = projectIoOperation
        val shouldWriteRecovery = hasUnsavedChanges &&
            operationAtShutdown != ProjectIoOperation.LOAD &&
            operationAtShutdown != ProjectIoOperation.RECOVER
        engine = null
        if (closingSession != null) {
            metadataExecutor.execute {
                try {
                    if (shouldWriteRecovery) closingSession.saveAutosave(projectPath)
                } catch (_: Exception) { }
                finally { closingSession.close() }
            }
        }
        metadataExecutor.shutdown()
        exportCancellation?.set(true)
        exportExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun updateEngineStatus() {
        try { engineStatusText.text = engine?.status() ?: "Engine বন্ধ" } catch (error: Exception) {
            engineStatusText.text = "Engine error: ${error.message}"
        }
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
        return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    companion object {
        private const val REQUEST_VIDEO = 4101
        private const val REQUEST_PROJECT_SAVE = 4102
        private const val REQUEST_PROJECT_OPEN = 4103
        private const val REQUEST_RELINK_MEDIA = 4104
        private const val REQUEST_EXPORT_VIDEO = 4105
    }
}
