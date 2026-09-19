package com.osfans.trime.sdk

/**
 * 编辑器返回的文本片段，不保证是全文。选区/组合索引为片段内 UTF-16 索引，-1 表示未知。
 * textOffset 为片段在编辑器中的起点；光标坐标为屏幕像素，缺失时为 null。
 * 该模型不包含 AI、网络、草稿或宿主业务对象，便于不同宿主复用。
 */
data class EditorSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val composingStart: Int = -1,
    val composingEnd: Int = -1,
    val cursor: EditorCursorAnchor? = null,
    val textOffset: Int = 0,
    val sessionId: Long = 0,
) {
    private val length: Int get() = text.length
    val hasValidSelection: Boolean get() = selectionStart in 0..length && selectionEnd in 0..length
    val safeSelectionStart: Int get() = selectionStart.coerceIn(0, length)
    val safeSelectionEnd: Int get() = selectionEnd.coerceIn(0, length)
    val hasComposition: Boolean get() = composingStart >= 0 && composingEnd > composingStart
    val safeComposingStart: Int get() = if (hasComposition) composingStart.coerceAtMost(length) else -1
    val safeComposingEnd: Int get() = if (hasComposition) composingEnd.coerceAtMost(length) else -1
    val hasSelection: Boolean get() = hasValidSelection && selectionStart != selectionEnd
    val hasTextBefore: Boolean get() = hasValidSelection && minOf(selectionStart, selectionEnd) > 0
    val hasTextAfter: Boolean get() = hasValidSelection && maxOf(selectionStart, selectionEnd) < length
    val selectedText: String? get() = if (hasSelection) text.substring(minOf(selectionStart, selectionEnd), maxOf(selectionStart, selectionEnd)) else null
    val absoluteSelectionStart: Int get() = if (hasValidSelection) textOffset + selectionStart else -1
    val absoluteSelectionEnd: Int get() = if (hasValidSelection) textOffset + selectionEnd else -1
}

data class EditorCursorAnchor(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val valid: Boolean = true,
)
