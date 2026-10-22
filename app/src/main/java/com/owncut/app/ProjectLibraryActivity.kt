package com.owncut.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Launcher/project shelf. No native editing session or decoder survives behind the editor. */
class ProjectLibraryActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var library: ProjectLibrary
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private var busy = false
    private var nameDialogOpen = false
    private var listing = LibraryListing(emptyList(), emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = ProjectLibrary(filesDir)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
            setBackgroundColor(0xFF101713.toInt())
            fitsSystemWindows = true
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF101713.toInt())
            addView(body)
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        })
        render()
    }
    override fun onResume() { super.onResume(); if (!nameDialogOpen) refresh() }

    private fun refresh() = task("Project library পড়া হচ্ছে…") {
        val migrationWarning = try { library.migrateLegacy(); emptyList() }
            catch (error: Exception) { listOf("পুরোনো workspace migration হয়নি: ${error.message}; original রাখা আছে") }
        val result = library.list()
        val next = result.copy(warnings = migrationWarning + result.warnings)
        ui { listing = next; render() }
    }
    private fun render() {
        body.removeAllViews()
        label("OWNCUT  /  PROJECTS", 13f)
        label("আপনার কাজের জায়গা", 26f)
        label("Project খুলুন → media নিন → edit করুন → MP4 render করুন।\nপ্রতিটি project-এর আলাদা save ও recovery snapshot থাকে।", 14f)
        button("নতুন project") { askName("নতুন project", "") { name ->
            task("Project তৈরি হচ্ছে…") { val project = library.create(name); ui { open(project) } }
        } }
        button("Project file import…") {
            if (!busy) {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
                }, IMPORT)
            }
        }
        button("তালিকা refresh করুন") { refresh() }
        button("Workflow ও ক্ষমতা") {
            AlertDialog.Builder(this).setTitle("Owncut workspace")
                .setMessage("Projects: আলাদা local project, নাম বদল, file import।\nEdit: preview, timeline, playhead, split/trim/move/delete, Undo/Redo।\nMedia: source তথ্য ও relink।\nDeliver: বর্তমান 720-high SDR MP4 render ও Gallery save।\nFlow: বর্তমান preview/export route-এর read-only ব্যাখ্যা।\n\nপরিকল্পিত: editable typed nodes, effects, compositing, grading, keyframes, audio graph ও RAW। এগুলো এই build-এ চালু নয়।\n\nLocal library app-private; uninstall/data clear করলে হারাবে। গুরুত্বপূর্ণ project file বাইরে Save as করুন; original media-ও রাখুন।")
                .setPositiveButton("বুঝেছি", null).show()
        }
        status = label(if (busy) "অপেক্ষা করুন…" else "${listing.projects.size} projects", 13f)
        if (listing.projects.isEmpty()) label("এখনো project নেই। নতুন project তৈরি করুন অথবা আগের .owncut file import করুন।", 16f)
        for (project in listing.projects) {
            label(project.name, 20f)
            label("শেষ খোলা/রাখা: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(project.updatedAt))}" +
                if (project.recovery) "\nRecovery snapshot আছে — editor-এ Recover autosave দিয়ে দেখুন।" else "", 12f)
            button("খুলুন · ${project.name}") { if (!busy) open(project) }
            button("নাম বদল · ${project.name}") { askName("Project-এর নাম", project.name) { name ->
                task("নাম বদল হচ্ছে…") {
                    library.rename(project.id, name)
                    val next = library.list()
                    ui { listing = next; render() }
                }
            } }
        }
        listing.warnings.forEach { label(it, 12f) }
    }
    private fun open(project: LibraryProject) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_LIBRARY_ID, project.id))
    }
    private fun askName(title: String, initial: String, action: (String) -> Unit) {
        if (busy) return
        val input = EditText(this).apply { setSingleLine(); setText(initial); hint = "Project name (১–৮০ অক্ষর)" }
        nameDialogOpen = true
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(input)
            .setNegativeButton("বাতিল", null).setPositiveButton("রাখুন", null).create()
        dialog.setOnDismissListener { nameDialogOpen = false }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim()
                if (value.isEmpty() || value.length > 80 || value.any { it.isISOControl() }) input.error = "১–৮০ অক্ষরের নাম দিন"
                else { dialog.dismiss(); action(value) }
            }
        }
        dialog.show()
    }
    private fun task(message: String, action: () -> Unit) {
        if (busy) return
        busy = true; status.text = message
        worker.execute {
            try { action(); ui { busy = false; status.text = "${listing.projects.size} projects" } }
            catch (error: Exception) { ui { busy = false; status.text = "কাজ হয়নি: ${error.message}" } }
        }
    }
    private fun ui(action: () -> Unit) = runOnUiThread { if (!isDestroyed && !isFinishing) action() }
    private fun label(text: String, size: Float) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(0xFFE5EEE8.toInt()); setPadding(0, dp(8), 0, dp(6))
        body.addView(this, ViewGroup.LayoutParams(-1, -2))
    }
    private fun button(text: String, action: () -> Unit) {
        body.addView(Button(this).apply { this.text = text; setOnClickListener { if (!busy) action() } }, ViewGroup.LayoutParams(-1, -2))
    }
    @Deprecated("Legacy SAF result adapter")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (requestCode != IMPORT || resultCode != RESULT_OK) return
        askName("Imported project-এর নাম", "Imported project") { name ->
            task("Project import হচ্ছে…") {
                val temporary = File.createTempFile("library-import-", ".owncut", cacheDir)
                try {
                    contentResolver.openInputStream(uri)?.use { input -> temporary.outputStream().use { ProjectDocumentIo.copy(input, it) } }
                        ?: error("Project file পড়া যাচ্ছে না")
                    val project = library.create(name, temporary)
                    ui { open(project) }
                } finally { temporary.delete() }
            }
        }
    }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { private const val IMPORT = 5201 }
}
