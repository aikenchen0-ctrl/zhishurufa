package com.osfans.trime.sdk

/**
 * 当前宿主编辑器的只读上下文。文本和索引来自 InputConnection，坐标来自 CursorAnchorInfo。
 * 该模型不包含 AI、网络、草稿或宿主业务对象，便于不同宿主复用。
 */
data class EditorSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val composingStart: Int = -1,
    val composingEnd: Int = -1,
    val cursor: EditorCursorAnchor? = null,
) {
    private val length: Int get() = text.length
    val safeSelectionStart: Int get() = selectionStart.coerceIn(0, length)
    val safeSelectionEnd: Int get() = selectionEnd.coerceIn(0, length)
    val safeComposingStart: Int get() = composingStart.coerceIn(0, length)
    val safeComposingEnd: Int get() = composingEnd.coerceIn(0, length)
    val hasSelection: Boolean get() = safeSelectionStart != safeSelectionEnd
    val hasTextBefore: Boolean get() = safeSelectionStart > 0
    val hasTextAfter: Boolean get() = safeSelectionEnd < length
}

data class EditorCursorAnchor(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val valid: Boolean = true,
)
