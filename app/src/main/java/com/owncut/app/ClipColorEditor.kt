package com.owncut.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Original slider-based grade editor. C++ validates/grades; UI only transports values.
 * A single in-flight still render plus one latest pending revision bounds work. Saving
 * validated settings is independent of decoder availability; never claim unavailable preview.
 */
@UnstableApi
internal object ClipColorEditor {
    fun show(activity: Activity, clip: ClipSnapshot, positionMs: Long, previewDraft: ((ColorGrade) -> AlertDialog)?=null, commit: (ColorGrade) -> Unit): AlertDialog {
        require(clip.sourceInMs>=0 && positionMs>=clip.sourceInMs && positionMs<clip.sourceOutMs)
        val main=Handler(Looper.getMainLooper())
        val worker=Executors.newSingleThreadExecutor()
        val closed=AtomicBoolean(false)
        fun dp(value: Int)=(activity.resources.displayMetrics.density*value).toInt()
        val root=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(8)) }
        val preview=ImageView(activity).apply { adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER }
        root.addView(preview,LinearLayout.LayoutParams(-1,dp(160)))
        root.addView(TextView(activity).apply { text=AppLanguage.text(R.string.color_slider_help);setTextColor(StudioUi.muted) })
        val status=TextView(activity).apply { text=AppLanguage.text(R.string.color_processing);setTextIsSelectable(true) }
        root.addView(status)
        val enabled=CheckBox(activity).apply { text=AppLanguage.text(R.string.color_enabled);isChecked=clip.grade.enabled }
        root.addView(enabled)
        val values=clip.grade.parametersV1()
        val names=activity.resources.getStringArray(R.array.color_parameter_names)
        check(names.size==values.size)
        val scales=values.mapIndexed { index,value -> ColorSliderScale.forParameter(index,value) }
        val bars=mutableListOf<SeekBar>()
        val readouts=mutableListOf<TextView>()
        val groups=List(3) { LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL } }
        val tabs=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL }
        root.addView(tabs)
        val tabButtons=mutableListOf<Button>()
        fun showGroup(group: Int) {
            groups.forEachIndexed { index,view -> view.visibility=if(index==group) android.view.View.VISIBLE else android.view.View.GONE }
            tabButtons.forEachIndexed { index,button -> button.isSelected=index==group;button.setTextColor(if(index==group) StudioUi.accent else StudioUi.muted) }
        }
        listOf(R.string.color_group_primary,R.string.color_group_balance,R.string.color_group_tonal).forEachIndexed { index,label ->
            val button=Button(activity).apply {
                text=AppLanguage.text(label);isAllCaps=false
                setOnClickListener { showGroup(index) }
            }
            tabButtons.add(button);tabs.addView(button,LinearLayout.LayoutParams(0,-2,1f))
        }
        values.forEachIndexed { index,value ->
            val row=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
            val label=TextView(activity).apply { setTextColor(StudioUi.text) }
            readouts.add(label)
            row.addView(label)
            val bar=SeekBar(activity).apply {
                max=ColorSliderScale.STEPS;progress=scales[index].progress(value)
                contentDescription=names[index];minimumHeight=dp(48)
                progressTintList=android.content.res.ColorStateList.valueOf(StudioUi.accent)
                thumbTintList=android.content.res.ColorStateList.valueOf(StudioUi.accent)
            }
            bars.add(bar);row.addView(bar)
            groups[when(index) { in 0..4 -> 0; in 5..7 -> 1; else -> 2 }].addView(row)
        }
        groups.forEach { root.addView(it) };showGroup(0)
        fun labels() { readouts.forEachIndexed { index,label -> label.text=names[index]+"  "+String.format(java.util.Locale.ROOT,"%.3f",values[index]);bars[index].contentDescription=label.text } }
        labels()
        val buttons=LinearLayout(activity)
        val render=Button(activity).apply { text=AppLanguage.text(R.string.color_render_still);isEnabled=false }
        val compare=ToggleButton(activity).apply {
            textOn=AppLanguage.text(R.string.color_before);textOff=AppLanguage.text(R.string.color_after);isChecked=false;isEnabled=false
        }
        buttons.addView(render,LinearLayout.LayoutParams(0,-2,1f));buttons.addView(compare,LinearLayout.LayoutParams(0,-2,1f))
        root.addView(buttons)
        val reset=Button(activity).apply { text=AppLanguage.text(R.string.color_reset_draft) };root.addView(reset)
        val live=Button(activity).apply { text=AppLanguage.text(R.string.color_preview_draft);isEnabled=previewDraft!=null };root.addView(live)
        val dialog=AlertDialog.Builder(activity).setTitle(AppLanguage.text(R.string.color_editor_title))
            .setView(ScrollView(activity).apply { addView(root) })
            .setPositiveButton(AppLanguage.text(R.string.color_apply),null)
            .setNegativeButton(AppLanguage.text(R.string.color_cancel),null).create()
        var draftPlayer: AlertDialog?=null
        var original: Bitmap?=null
        var graded: Bitmap?=null
        var source: IntArray?=null // extraction/render worker only
        var revision=0L
        var busy=false
        var pending=false
        fun draft()=ColorGrade.fromParametersV1(values.copyOf(),enabled.isChecked)
        fun validatedDraft(): ColorGrade {
            val settings=draft()
            ColorEngine.shaderSources(settings,ColorEngine.TextureEncoding.MEDIA3_SDR_709)
            return settings
        }
        lateinit var debounced: Runnable
        fun updateButtons() { render.isEnabled=original!=null && !busy }
        fun requestRender() { main.removeCallbacks(debounced);main.postDelayed(debounced,220) }
        fun startRender() {
            if(closed.get()) return
            val frame=original ?: return
            if(busy) { pending=true;return }
            try {
                val settings=validatedDraft()
                val captured=revision
                pending=false;busy=true;compare.isEnabled=false;updateButtons()
                status.text=AppLanguage.text(R.string.color_processing)
                worker.execute {
                    try {
                        check(!closed.get())
                        val pixels=ColorFrameBuffers.render(source ?: error("Missing color frame"),frame.width,frame.height,settings)
                        check(!closed.get())
                        val result=Bitmap.createBitmap(pixels,frame.width,frame.height,Bitmap.Config.ARGB_8888)
                        main.post {
                            if(!closed.get()) {
                                busy=false
                                if(captured==revision) {
                                    graded=result;preview.setImageBitmap(if(compare.isChecked) original else result)
                                    compare.isEnabled=true;status.text=AppLanguage.text(R.string.color_auto_preview_ready)
                                }
                                updateButtons()
                                if(pending || captured!=revision) requestRender()
                            }
                        }
                    } catch(error: Exception) { main.post {
                        if(!closed.get()) {
                            busy=false;updateButtons()
                            if(captured==revision) status.text=AppLanguage.text(R.string.color_preview_unavailable,ColorPreviewFailure.describe(error))
                            if(pending || captured!=revision) requestRender()
                        }
                    } }
                }
            } catch(error: Exception) { status.text=AppLanguage.text(R.string.color_error,ColorPreviewFailure.describe(error)) }
        }
        debounced=Runnable { startRender() }
        fun changed() {
            revision++;compare.isEnabled=false
            status.text=AppLanguage.text(R.string.color_draft_changed)
            requestRender()
        }
        bars.forEachIndexed { index,bar -> bar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view: SeekBar,progress: Int,fromUser: Boolean) {
                if(fromUser) { values[index]=scales[index].value(progress);labels();changed() }
            }
            override fun onStartTrackingTouch(view: SeekBar) {}
            override fun onStopTrackingTouch(view: SeekBar) { requestRender() }
        }) }
        enabled.setOnCheckedChangeListener { _,_ -> changed() }
        reset.setOnClickListener {
            ColorGrade().parametersV1().copyInto(values)
            bars.forEachIndexed { index,bar -> bar.progress=scales[index].progress(values[index]) }
            enabled.isChecked=true;labels();changed()
        }
        compare.setOnCheckedChangeListener { _,before -> preview.setImageBitmap(if(before) original else graded ?: original) }
        render.setOnClickListener { requestRender() }
        live.setOnClickListener {
            try { draftPlayer?.dismiss();draftPlayer=previewDraft?.invoke(validatedDraft()) }
            catch(error: Exception) { status.text=AppLanguage.text(R.string.color_error,ColorPreviewFailure.describe(error)) }
        }
        dialog.setOnDismissListener {
            closed.set(true);main.removeCallbacks(debounced);draftPlayer?.dismiss();draftPlayer=null
            worker.shutdownNow();preview.setImageDrawable(null);original=null;graded=null
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    commit(validatedDraft())
                    Toast.makeText(activity,AppLanguage.text(R.string.color_saved_notice),Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                } catch(error: Exception) { status.text=AppLanguage.text(R.string.color_error,ColorPreviewFailure.describe(error)) }
            }
        }
        dialog.show()
        worker.execute {
            try {
                val metadata=VideoSourceProbe.read(activity.applicationContext,clip.mediaId) { check(!closed.get()) }
                ColorEngine.validateVideoSource(metadata.color)
                check(!closed.get())
                val requested=PreviewFrameSize.fit(metadata.width,metadata.height,metadata.rotationDegrees)
                if(Build.VERSION.SDK_INT<27) require(PreviewFrameSize.fitsUnscaled(metadata.width,metadata.height,metadata.rotationDegrees)) {
                    AppLanguage.text(R.string.color_scaled_preview_requires_27)
                }
                val retriever=MediaMetadataRetriever()
                val frame=try {
                    retriever.setDataSource(activity.applicationContext,Uri.parse(clip.mediaId))
                    check(!closed.get())
                    val timeUs=Math.multiplyExact(positionMs,1000L)
                    val decoded=requireNotNull(if(Build.VERSION.SDK_INT>=27)
                        retriever.getScaledFrameAtTime(timeUs,MediaMetadataRetriever.OPTION_CLOSEST,requested.width,requested.height)
                        else retriever.getFrameAtTime(timeUs,MediaMetadataRetriever.OPTION_CLOSEST))
                    check(!closed.get())
                    require(decoded.colorSpace?.isSrgb == true && decoded.config==Bitmap.Config.ARGB_8888) { "Still preview requires an sRGB ARGB8888 bitmap" }
                    require(decoded.width<=PreviewFrameSize.MAX_WIDTH && decoded.height<=PreviewFrameSize.MAX_HEIGHT) {
                        "Decoder returned a bitmap beyond the requested preview budget"
                    }
                    decoded
                } finally { retriever.release() }
                check(!closed.get())
                val pixels=IntArray(frame.width*frame.height)
                frame.getPixels(pixels,0,frame.width,0,0,frame.width,frame.height) // straight ARGB sRGB ints
                source=pixels
                main.post { if(!closed.get()) { original=frame;preview.setImageBitmap(frame);startRender() } }
            } catch(error: Exception) { main.post { if(!closed.get()) status.text=AppLanguage.text(R.string.color_preview_unavailable,ColorPreviewFailure.describe(error)) } }
        }
        return dialog
    }
}
