package com.owncut.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.WindowInsets
import android.view.ViewGroup
import android.widget.Button
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

class MainActivity : Activity(), SurfaceHolder.Callback {
    private var engine: EngineSession? = null
    private lateinit var preview: AspectRatioSurfaceView
    private lateinit var statusText: TextView
    private lateinit var engineStatusText: TextView
    private lateinit var mediaText: TextView
    private lateinit var timelineContainer: LinearLayout
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
    private var pendingAutosave: ScheduledFuture<*>? = null
    private var metadataRequestSequence = 0
    @Volatile private var playerGeneration = 0
    private val playbackTicker = object : Runnable {
        override fun run() {
            val generation = playerGeneration
            mediaHandler.post {
                val current = player
                val tick = try {
                    if (current != null && current.isPlaying) {
                        val position = current.currentPosition
                        val end = previewEndMs.coerceAtMost(current.duration.toLong()).coerceAtLeast(0L).toInt()
                        if (position >= end) {
                            current.pause()
                            current.seekTo(end)
                            end to true
                        } else position to false
                    } else null
                } catch (_: IllegalStateException) { null }
                if (tick != null) mainHandler.post {
                    if (generation == playerGeneration && player === current) {
                        val (position, reachedClipEnd) = tick
                        if (!isSeeking) seekBar.progress = position
                        if (reachedClipEnd) {
                            advanceOrStopTimeline(generation, current)
                        } else {
                            statusText.text = "Playback ${formatTime(position.toLong())} / ${formatTime(durationMs)}"
                            mainHandler.postDelayed(this, 200L)
                        }
                    }
                }
            }
        }
    }
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    private var selectedUri: Uri? = null
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var playerPrepared = false
    private var surfaceReady = false
    private var durationMs = 0L
    private var previewStartMs = 0L
    private var previewEndMs = 0L
    private var isSeeking = false

    private enum class ProjectIoOperation { SAVE, LOAD, RECOVER }

    private data class RetainedEditorState(
        val engine: EngineSession,
        val projectPath: String,
        val selectedUri: String?,
        val selectedClipId: String?,
        val hasUnsavedChanges: Boolean,
        val editGeneration: Long,
    )

    private var projectIoOperation: ProjectIoOperation? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val retained = lastNonConfigurationInstance as? RetainedEditorState
        if (retained != null) {
            engine = retained.engine
            projectPath = retained.projectPath
            selectedUri = retained.selectedUri?.let { Uri.parse(it) }
            selectedClipId = retained.selectedClipId
            hasUnsavedChanges = retained.hasUnsavedChanges
            editGeneration = retained.editGeneration
        } else {
            engine = EngineSession()
            projectPath = File(filesDir, "active.owncut").absolutePath
        }
        buildWorkspace()
        preview.holder.addCallback(this)
        if (retained != null) {
            selectedUri?.let { readMetadataAndRegister(it, existingClipId = selectedClipId) }
            if (hasUnsavedChanges) scheduleAutosave()
        } else if (savedInstanceState != null) {
            statusText.text = "আগের session-এর জন্য Recover autosave চাপুন, অথবা media আবার নির্বাচন করুন।"
        }
        updateEngineStatus()
        refreshTimeline()
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

        preview = AspectRatioSurfaceView(this).apply {
            setBackgroundColor(0xFF080B09.toInt())
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
        val timelineScroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
        }
        timelineContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        timelineScroll.addView(timelineContainer)
        root.addView(timelineScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92)))

        val editScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val editControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        editControls.addView(actionButton("Split") { splitAtPlayhead() }, wrapButtonParams())
        editControls.addView(actionButton("Trim L") { trimAtPlayhead(leftEdge = true) }, wrapButtonParams())
        editControls.addView(actionButton("Trim R") { trimAtPlayhead(leftEdge = false) }, wrapButtonParams())
        editControls.addView(actionButton("Delete") { deleteSelected(ripple = false) }, wrapButtonParams())
        editControls.addView(actionButton("Ripple") { deleteSelected(ripple = true) }, wrapButtonParams())
        editScroll.addView(editControls)
        root.addView(editScroll, matchWrap())

        val projectControls = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val projectButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        projectButtons.addView(actionButton("Undo") { runHistory(undo = true) }, wrapButtonParams())
        projectButtons.addView(actionButton("Redo") { runHistory(undo = false) }, wrapButtonParams())
        projectButtons.addView(actionButton("Save") { saveProject() }, wrapButtonParams())
        projectButtons.addView(actionButton("Open saved") { confirmLoadProject() }, wrapButtonParams())
        projectButtons.addView(actionButton("Recover autosave") { confirmRecoverAutosave() }, wrapButtonParams())
        projectControls.addView(projectButtons)
        root.addView(projectControls, matchWrap())

        seekBar = SeekBar(this).apply {
            max = 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) statusText.text = "Seek: ${formatTime(progress.toLong())} / ${formatTime(durationMs)}"
                }
                override fun onStartTrackingTouch(bar: SeekBar) { isSeeking = true }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    isSeeking = false
                    if (playerPrepared) {
                        val current = player
                        val position = bar.progress
                        mediaHandler.post { if (player === current) current?.seekTo(position) }
                    }
                }
            })
        }
        root.addView(seekBar, matchWrap())

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val importButton = Button(this).apply {
            text = "ভিডিও নিন"
            setOnClickListener { launchVideoPicker() }
        }
        playButton = Button(this).apply {
            text = "চালান"
            isEnabled = false
            setOnClickListener { togglePlayback() }
        }
        controls.addView(importButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
            statusText.text = "Timeline পড়া যায়নি: ${error.message}"
            return
        }
        if (selectedClipId != null && clips.none { it.id == selectedClipId }) selectedClipId = null
        timelineContainer.removeAllViews()
        for (clip in clips) {
            val selected = clip.id == selectedClipId
            val row = TextView(this).apply {
                text = "${if (selected) "▶ " else ""}${clip.trackId} · ${clip.id} · ${formatTime(frameToMs(clip.startFrame))}–${formatTime(frameToMs(clip.startFrame + clip.durationFrames))}"
                textSize = 13f
                setTextColor(if (selected) 0xFFE5EEE8.toInt() else 0xFFB5C6BB.toInt())
                setBackgroundColor(if (selected) 0xFF294638.toInt() else 0xFF18221C.toInt())
                setPadding(dp(10), dp(7), dp(10), dp(7))
                isClickable = true
                isFocusable = true
                setOnClickListener { selectClip(clip) }
            }
            timelineContainer.addView(row, matchWrap().apply { bottomMargin = dp(3) })
        }
        val selected = clips.firstOrNull { it.id == selectedClipId }
        selectedClipText.text = selected?.let {
            "Timeline · ${it.id} · source ${formatTime(it.sourceInMs)}–${formatTime(it.sourceOutMs)}"
        } ?: "Timeline · ${clips.size} clip(s) · কোনো clip নির্বাচিত নয়"
    }

    private fun selectClip(clip: ClipSnapshot, resumePlayback: Boolean = false) {
        if (projectIoBusy) return
        resumeAfterSelection = resumePlayback
        selectedClipId = clip.id
        releasePlayer()
        val uri = Uri.parse(clip.mediaId)
        selectedUri = uri
        previewStartMs = clip.sourceInMs
        previewEndMs = clip.sourceOutMs
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
            playButton.text = "চালান"
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
                releasePlayer()
                mediaText.text = "Timeline-এ কোনো clip নেই"
            }
        } catch (error: Exception) { showError(error) }
    }

    private fun runHistory(undo: Boolean) {
        if (blockWhileMediaLoads()) return
        val session = engine ?: return
        try {
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
            clips.firstOrNull { it.id == selectedClipId }?.let { selectClip(it) }
        } catch (error: Exception) { showError(error) }
    }

    private fun scheduleAutosave() {
        val session = engine ?: return
        hasUnsavedChanges = true
        editGeneration += 1
        pendingAutosave?.cancel(false)
        pendingAutosave = metadataExecutor.schedule({
            try {
                session.saveAutosave(projectPath)
                mainHandler.post { if (!isDestroyed) statusText.text = "Autosave محفوظ হয়েছে" }
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
        if (!selectedMediaLoading) return false
        Toast.makeText(this, "Selected media প্রস্তুত হচ্ছে", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun showError(error: Exception) {
        Toast.makeText(this, error.message ?: "Edit operation ব্যর্থ", Toast.LENGTH_LONG).show()
    }

    private fun frameToMs(frames: Long): Long = ((frames.coerceAtLeast(0L).toDouble() * 1000.0) / 30.0).toLong()

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
        if (requestCode != REQUEST_VIDEO || resultCode != RESULT_OK) return
        val resultData = data ?: return
        val uri = resultData.data ?: return
        val takeGrant = resultData.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        readMetadataAndRegister(uri, takeGrant)
    }

    private fun readMetadataAndRegister(
        uri: Uri,
        takePersistableGrant: Boolean = false,
        existingClipId: String? = null,
    ) {
        val request = ++metadataRequestSequence
        selectedMediaLoading = true
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
                    val clipId = existing?.id ?: session.addMediaClip(uri.toString(), duration)
                    val addedNewClip = existing == null
                    selectedClipId = clipId
                    previewStartMs = existing?.sourceInMs ?: 0L
                    previewEndMs = existing?.sourceOutMs ?: duration
                    selectedUri = uri
                    durationMs = duration
                    preview.setVideoSize(displayWidth, displayHeight)
                    seekBar.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
                    seekBar.progress = 0
                    val grantInfo = if (persistedGrant) {
                        "SAF read access সংরক্ষিত; Original media অপরিবর্তিত থাকবে।"
                    } else {
                        "সতর্কতা: স্থায়ী access পাওয়া যায়নি; পরে media relink লাগতে পারে।"
                    }
                    mediaText.text = "${uri.lastPathSegment ?: "Video"} · ${displayWidth}×${displayHeight} · ${formatTime(duration)} · $clipId\n$grantInfo"
                    if (surfaceReady) preparePlayer(uri)
                    updateEngineStatus()
                    refreshTimeline()
                    if (addedNewClip) scheduleAutosave()
                } catch (error: Exception) {
                    mediaText.text = "ভিডিও timeline-এ যোগ হয়নি: ${error.message}"
                }
            }
        }
    }

    private fun preparePlayer(uri: Uri) {
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
                candidate.setDisplay(preview.holder)
                candidate.setOnPreparedListener { ready ->
                    if (generation != playerGeneration || player !== ready) return@setOnPreparedListener
                    playerPrepared = true
                    val readyDuration = ready.duration
                    val startPosition = previewStartMs.coerceIn(0L, readyDuration.toLong().coerceAtLeast(0L))
                    val shouldResume = resumeAfterSelection
                    resumeAfterSelection = false
                    if (startPosition > 0L) ready.seekTo(startPosition.toInt())
                    if (shouldResume) {
                        try {
                            ready.start()
                        } catch (error: IllegalStateException) {
                            playerPrepared = false
                            resumeAfterSelection = false
                            mainHandler.post {
                                if (generation == playerGeneration && player === ready) {
                                    playButton.isEnabled = false
                                    statusText.text = "পরের clip চালু হয়নি: ${error.message}"
                                }
                            }
                            return@setOnPreparedListener
                        }
                    }
                    mainHandler.post {
                        if (generation == playerGeneration && player === ready) {
                            durationMs = readyDuration.toLong()
                            seekBar.max = readyDuration.coerceAtLeast(1)
                            seekBar.progress = startPosition.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                            playButton.isEnabled = true
                            if (shouldResume) {
                                playButton.text = "থামান"
                                mainHandler.removeCallbacks(playbackTicker)
                                mainHandler.post(playbackTicker)
                            } else statusText.text = "Preview প্রস্তুত · ${formatTime(durationMs)}"
                        }
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
                        failed.release()
                        mainHandler.post {
                            mainHandler.removeCallbacks(playbackTicker)
                            playButton.isEnabled = false
                            statusText.text = "Decoder error ($what/$extra); এই device-এ format সমর্থিত নাও হতে পারে।"
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
                }
                candidate.release()
                mainHandler.post {
                    if (generation == playerGeneration) statusText.text = "Preview চালু হয়নি: ${error.message ?: "decoder error"}"
                }
            }
        }
    }

    private fun togglePlayback() {
        val current = player
        if (!playerPrepared || current == null) return
        val generation = playerGeneration
        mediaHandler.post {
            if (generation != playerGeneration || player !== current) return@post
            try {
                if (current.isPlaying) {
                    current.pause()
                    mainHandler.post {
                        mainHandler.removeCallbacks(playbackTicker)
                        playButton.text = "চালান"
                        statusText.text = "বিরতি"
                    }
                } else {
                    val position = current.currentPosition.toLong()
                    if (position < previewStartMs || position >= previewEndMs) {
                        current.seekTo(previewStartMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    }
                    current.start()
                    mainHandler.post {
                        playButton.text = "থামান"
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
        val old = player
        player = null
        playerPrepared = false
        playerGeneration += 1
        playButtonOrNull()?.let { it.isEnabled = false; it.text = "চালান" }
        if (old != null) mediaHandler.post {
            try { old.setDisplay(null) } catch (_: IllegalStateException) { }
            try { old.release() } catch (_: IllegalStateException) { }
        }
    }

    private fun playButtonOrNull(): Button? = if (::playButton.isInitialized) playButton else null

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        selectedUri?.let(::preparePlayer)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        releasePlayer()
    }

    override fun onStart() {
        super.onStart()
        if (surfaceReady) selectedUri?.let(::preparePlayer)
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    @Suppress("DEPRECATION") // Retain the native session without adding a ViewModel dependency to this bootstrap.
    override fun onRetainNonConfigurationInstance(): Any? {
        if (projectIoBusy) return null
        val currentEngine = engine ?: return null
        val retained = RetainedEditorState(
            engine = currentEngine,
            projectPath = projectPath,
            selectedUri = selectedUri?.toString(),
            selectedClipId = selectedClipId,
            hasUnsavedChanges = hasUnsavedChanges,
            editGeneration = editGeneration,
        )
        handingOffEngine = true
        engine = null
        return retained
    }

    override fun onDestroy() {
        metadataRequestSequence += 1
        if (!handingOffEngine) pendingAutosave?.cancel(false)
        preview.holder.removeCallback(this)
        releasePlayer()
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
    }
}
