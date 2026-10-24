package com.owncut.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.*
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Actual native-processed STILL preview, explicitly not graded playback. One worker owns
 * extraction/buffers. Draft stays outside history; Apply is one atomic native clip command.
 * Source metadata/bitmap color space must be known; never reinterpret HDR as sRGB.
 */
@UnstableApi
internal object ClipColorEditor {
    fun show(activity: Activity, clip: ClipSnapshot, positionMs: Long, commit: (ColorGrade) -> Unit): AlertDialog {
        require(clip.sourceInMs>=0 && positionMs>=clip.sourceInMs && positionMs<clip.sourceOutMs)
        val main=Handler(Looper.getMainLooper())
        val worker=Executors.newSingleThreadExecutor()
        val closed=AtomicBoolean(false)
        val root=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setPadding(24,16,24,16) }
        val preview=ImageView(activity).apply { adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER }
        root.addView(preview,LinearLayout.LayoutParams(-1,(activity.resources.displayMetrics.density*180).toInt()))
        val status=TextView(activity).apply { text=AppLanguage.text(R.string.color_still_notice) }
        root.addView(status)
        val enabled=CheckBox(activity).apply { text=AppLanguage.text(R.string.color_enabled);isChecked=clip.grade.enabled }
        root.addView(enabled)
        val fields=mutableListOf<EditText>()
        val names=activity.resources.getStringArray(R.array.color_parameter_names)
        check(names.size==17)
        clip.grade.parametersV1().forEachIndexed { index,value ->
            root.addView(TextView(activity).apply { text=names[index] })
            root.addView(EditText(activity).apply {
                inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                setSingleLine();setText(value.toString());contentDescription=names[index];fields.add(this)
            })
        }
        val buttons=LinearLayout(activity)
        val render=Button(activity).apply { text=AppLanguage.text(R.string.color_render_still);isEnabled=false }
        val compare=ToggleButton(activity).apply {
            textOn=AppLanguage.text(R.string.color_before);textOff=AppLanguage.text(R.string.color_after);isChecked=false;isEnabled=false
        }
        val reset=Button(activity).apply { text=AppLanguage.text(R.string.color_reset_draft) }
        buttons.addView(render,LinearLayout.LayoutParams(0,-2,1f));buttons.addView(compare,LinearLayout.LayoutParams(0,-2,1f))
        root.addView(buttons);root.addView(reset)
        val dialog=AlertDialog.Builder(activity).setTitle(AppLanguage.text(R.string.color_editor_title))
            .setView(ScrollView(activity).apply { addView(root) })
            .setPositiveButton(AppLanguage.text(R.string.color_apply),null)
            .setNegativeButton(AppLanguage.text(R.string.color_cancel),null).create()
        var original: Bitmap?=null
        var graded: Bitmap?=null
        var source: IntArray?=null // worker only after extraction
        var accepted: ColorGrade?=null
        var busy=false
        fun draft()=ColorGradeDraft.parse(fields.map { it.text.toString() },enabled.isChecked)
        fun updateButtons() {
            render.isEnabled=!busy && original != null
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled=!busy && accepted != null
        }
        fun dirty() { accepted=null;dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled=false }
        for(field in fields) field.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { dirty() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        enabled.setOnCheckedChangeListener { _,_ -> dirty() }
        reset.setOnClickListener {
            ColorGrade().parametersV1().forEachIndexed { index,value -> fields[index].setText(value.toString()) }
            enabled.isChecked=true;dirty()
        }
        compare.setOnCheckedChangeListener { _,before -> preview.setImageBitmap(if(before) original else graded ?: original) }
        render.setOnClickListener {
            try {
                val settings=draft()
                val frame=original ?: return@setOnClickListener
                busy=true;accepted=null;updateButtons();status.text=AppLanguage.text(R.string.color_processing)
                // Snapshot the draft. Editing while busy cannot authorize applying a different draft.
                worker.execute {
                    try {
                        check(!closed.get())
                        ColorEngine.shaderSources(settings,ColorEngine.TextureEncoding.MEDIA3_SDR_709) // shared GPU budget
                        val input=source ?: error("Missing color frame")
                        val pixels=ColorFrameBuffers.render(input,frame.width,frame.height,settings)
                        check(!closed.get())
                        val result=Bitmap.createBitmap(pixels,frame.width,frame.height,Bitmap.Config.ARGB_8888)
                        main.post {
                            if(!closed.get()) {
                                graded=result;preview.setImageBitmap(if(compare.isChecked) original else result)
                                accepted=if(runCatching { draft()==settings }.getOrDefault(false)) settings else null
                                busy=false;compare.isEnabled=true;updateButtons();status.text=AppLanguage.text(R.string.color_still_notice)
                            }
                        }
                    } catch(error: Exception) { main.post { if(!closed.get()) {
                        busy=false;accepted=null;updateButtons();status.text=AppLanguage.text(R.string.color_error,error.message ?: error.javaClass.simpleName)
                    } } }
                }
            } catch(error: Exception) { status.text=AppLanguage.text(R.string.color_error,error.message ?: error.javaClass.simpleName) }
        }
        dialog.setOnDismissListener {
            closed.set(true);worker.shutdownNow();preview.setImageDrawable(null);original=null;graded=null
            // Do not recycle a bitmap while a posted native/RenderThread consumer might still hold it.
        }
        dialog.setOnShowListener {
            updateButtons()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val settings=accepted ?: return@setOnClickListener
                try { commit(settings);dialog.dismiss() }
                catch(error: Exception) { status.text=AppLanguage.text(R.string.color_error,error.message ?: error.javaClass.simpleName) }
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
                main.post { if(!closed.get()) { original=frame;preview.setImageBitmap(frame);updateButtons();render.performClick() } }
            } catch(error: Exception) { main.post { if(!closed.get()) status.text=AppLanguage.text(R.string.color_error,error.message ?: error.javaClass.simpleName) } }
        }
        return dialog
    }
}
