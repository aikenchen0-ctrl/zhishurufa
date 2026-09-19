package com.osfans.trime.sdk

import kotlinx.serialization.Serializable

@Serializable
enum class DraftChange { UNCHANGED, ADDED, REMOVED }

@Serializable
data class DraftSegment(val text: String, val change: DraftChange = DraftChange.UNCHANGED)

/** 只承载宿主提供的展示文本；不包含聊天记录、联系人身份或可持久化的编辑器会话。 */
@Serializable
data class DraftItem(
    val id: String,
    val title: String,
    val segments: List<DraftSegment>,
    /** 仅存活于当前进程；本地草稿恢复后必须重新生成，避免保存输入框上下文。 */
    @kotlinx.serialization.Transient val aiRequest: AiDraftRequest? = null,
) {
    init {
        require(id.isNotBlank() && id.length <= 128) { "草稿标识无效" }
        require(title.length <= 80) { "草稿标题过长" }
        require(segments.size <= 256 && segments.sumOf { it.text.length.toLong() } <= 16_384) { "草稿内容过长" }
    }
    val text: String get() = segments.filter { it.change != DraftChange.REMOVED }.joinToString("") { it.text }
    val originalText: String get() = segments.filter { it.change != DraftChange.ADDED }.joinToString("") { it.text }
}

enum class DraftSurface { HOST, IME }

data class DraftState internal constructor(
    val items: List<DraftItem> = emptyList(),
    val selectedId: String? = null,
    val owner: Long? = null,
    val canRegenerate: Boolean = false,
) {
    val selected: DraftItem? get() = items.firstOrNull { it.id == selectedId }
}

fun interface DraftActionHandler {
    /** 回调必须快速返回，宿主异步生成后用 update(expected, replacement) 拒绝过期结果。 */
    fun regenerate(draft: DraftItem)
}
