package com.zhishurufa.sample

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.graphics.Rect
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast
import com.osfans.trime.sdk.TrimeSdk
import com.osfans.trime.sdk.ui.DraftPanelView
import com.osfans.trime.sdk.DraftItem
import com.osfans.trime.sdk.DraftSegment
import com.osfans.trime.sdk.LocalDraftStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : Activity() {
    private val draftScope = MainScope()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        setContentView(R.layout.sample_activity_main)
        findViewById<LinearLayout>(R.id.sample_content).addView(DraftPanelView(this), 0)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            findViewById<View>(R.id.sample_title).visibility = View.GONE
            findViewById<View>(R.id.ime_status).visibility = View.GONE
            findViewById<EditText>(R.id.input_test).minLines = 2
        }
        val root = findViewById<View>(R.id.sample_root)
        val padding = (16 * resources.displayMetrics.density).toInt()
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                view.setPadding(
                    padding + bars.left,
                    padding + bars.top,
                    padding + bars.right,
                    padding + maxOf(bars.bottom, ime.bottom),
                )
                if (ime.bottom > 0) {
                    view.post { revealEditorCursor() }
                }
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    padding + insets.systemWindowInsetLeft,
                    padding + insets.systemWindowInsetTop,
                    padding + insets.systemWindowInsetRight,
                    padding + insets.systemWindowInsetBottom,
                )
            }
            insets
        }
        root.requestApplyInsets()

        findViewById<Button>(R.id.enable_ime).setOnClickListener {
            TrimeSdk.openInputMethodSettings(this)
        }
        findViewById<Button>(R.id.select_ime).setOnClickListener {
            TrimeSdk.showInputMethodPicker(this)
        }
        findViewById<Button>(R.id.ime_settings).setOnClickListener {
            TrimeSdk.openSettings(this)
        }
        findViewById<Button>(R.id.capture_draft).setOnClickListener {
            val text = findViewById<EditText>(R.id.input_test).text.toString()
            if (text.isBlank()) {
                Toast.makeText(this, R.string.draft_empty, Toast.LENGTH_SHORT).show()
            } else {
                runCatching {
                    TrimeSdk.drafts.replace(TrimeSdk.drafts.state.value.items + DraftItem(UUID.randomUUID().toString(), getString(R.string.draft_title), listOf(DraftSegment(text))))
                }.onFailure { Toast.makeText(this, R.string.draft_operation_failed, Toast.LENGTH_SHORT).show() }
            }
        }
        findViewById<Button>(R.id.save_drafts).setOnClickListener {
            val items = TrimeSdk.drafts.state.value.items
            draftScope.launch {
                try {
                    LocalDraftStore(applicationContext).save(items)
                    Toast.makeText(this@MainActivity, R.string.drafts_saved, Toast.LENGTH_SHORT).show()
                } catch (_: java.io.IOException) {
                    Toast.makeText(this@MainActivity, R.string.draft_operation_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
        findViewById<Button>(R.id.restore_drafts).setOnClickListener {
            draftScope.launch {
                try {
                    TrimeSdk.drafts.replace(LocalDraftStore(applicationContext).load())
                    Toast.makeText(this@MainActivity, R.string.drafts_restored, Toast.LENGTH_SHORT).show()
                } catch (_: java.io.IOException) {
                    Toast.makeText(this@MainActivity, R.string.draft_operation_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        draftScope.cancel()
        super.onDestroy()
    }

    private fun revealEditorCursor() {
        val editor = currentFocus as? EditText ?: return
        val layout = editor.layout ?: return
        val line = layout.getLineForOffset(editor.selectionStart.coerceAtLeast(0))
        val rectangle = Rect(
            editor.paddingLeft,
            editor.paddingTop + layout.getLineTop(line),
            editor.width - editor.paddingRight,
            editor.paddingTop + layout.getLineBottom(line),
        )
        editor.requestRectangleOnScreen(rectangle, true)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateStatus()
    }

    private fun updateStatus() {
        val status =
            when {
                TrimeSdk.isSelected() -> R.string.ime_selected
                TrimeSdk.isEnabled() -> R.string.ime_enabled
                else -> R.string.ime_disabled
            }
        findViewById<TextView>(R.id.ime_status).setText(status)
    }
}
