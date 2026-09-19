package com.osfans.trime.ime.core

import android.graphics.RectF
import android.text.Spanned
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.osfans.trime.sdk.EditorAccessPolicy
import com.osfans.trime.sdk.EditorCommand
import com.osfans.trime.sdk.EditorCursorAnchor
import com.osfans.trime.sdk.EditorSnapshot
import java.util.concurrent.atomic.AtomicLong

/** 仅在主线程使用；绑定一次输入连接，结束后不保留文本或 Android 连接对象。 */
internal class EditorConnectionBridge {
    private var sessionId = 0L
    private var allowed = false
    private var cursor: EditorCursorAnchor? = null

    fun start(info: EditorInfo) {
        sessionId = nextSession.incrementAndGet()
        allowed = EditorAccessPolicy.canAccess(info.inputType, info.imeOptions)
        cursor = null
    }

    fun finish() {
        sessionId = 0
        allowed = false
        cursor = null
    }

    fun updateAnchor(info: CursorAnchorInfo) {
        // 第 0 个字符不等于插入点；目标编辑器没有提供插入标记时不推算坐标。
        val bounds = RectF(info.insertionMarkerHorizontal, info.insertionMarkerTop, info.insertionMarkerHorizontal, info.insertionMarkerBottom)
        info.matrix.mapRect(bounds)
        val valid = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all { it.isFinite() } &&
            info.insertionMarkerFlags and CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION != 0
        cursor = if (allowed && valid) EditorCursorAnchor(bounds.left, bounds.top, bounds.right, bounds.bottom) else null
    }

    fun invalidateAnchor() { cursor = null }

    fun read(connection: InputConnection?, enabled: Boolean): EditorSnapshot? {
        if (!enabled || !allowed || sessionId == 0L || connection == null) return null
        return try {
            val extracted = connection.getExtractedText(ExtractedTextRequest().apply {
                flags = InputConnection.GET_TEXT_WITH_STYLES
                hintMaxChars = EditorAccessPolicy.MAX_TEXT_LENGTH
            }, 0) ?: return null
            val text = extracted.text ?: return null
            // 不把增量更新或超限文本伪装成完整快照；不得为凑齐全文重复读取宿主内容。
            if (extracted.partialStartOffset >= 0 || extracted.startOffset < 0 || text.length > EditorAccessPolicy.MAX_TEXT_LENGTH) return null
            if (extracted.startOffset.toLong() + text.length > Int.MAX_VALUE) return null
            val spanned = text as? Spanned
            val composing = spanned?.getSpans(0, text.length, Any::class.java)?.filter {
                spanned.getSpanFlags(it) and Spanned.SPAN_COMPOSING != 0
            }.orEmpty()
            EditorSnapshot(
                text.toString(), extracted.selectionStart, extracted.selectionEnd,
                composingStart = composing.minOfOrNull { spanned!!.getSpanStart(it) } ?: -1,
                composingEnd = composing.maxOfOrNull { spanned!!.getSpanEnd(it) } ?: -1,
                cursor = cursor, textOffset = extracted.startOffset, sessionId = sessionId,
            )
        } catch (_: Exception) { null }
    }

    fun execute(connection: InputConnection?, enabled: Boolean, expected: EditorSnapshot, command: EditorCommand, composing: Boolean): Boolean {
        if (composing) return false
        val current = read(connection, enabled) ?: return false
        if (!EditorAccessPolicy.canEdit(expected, current, command)) return false
        val ic = connection ?: return false
        return try {
            when (command) {
                is EditorCommand.CommitText -> ic.commitText(command.text, 1)
                is EditorCommand.ReplaceSelection -> ic.commitText(command.text, 1)
                EditorCommand.DeleteSelection -> ic.commitText("", 1)
                EditorCommand.SelectAll -> ic.performContextMenuAction(android.R.id.selectAll)
            }
        } catch (_: Exception) { false }
    }

    companion object { private val nextSession = AtomicLong() }
}
