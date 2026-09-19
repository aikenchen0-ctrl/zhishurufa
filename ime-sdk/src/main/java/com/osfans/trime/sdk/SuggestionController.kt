package com.osfans.trime.sdk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/** 候选栏中的短预推荐；不携带联系人、聊天记录或编辑器会话。 */
data class SuggestionItem(
    val id: String,
    val text: String,
    val source: String? = null,
) {
    init {
        require(id.isNotBlank() && id.length <= 128) { "推荐标识无效" }
        require(text.isNotBlank() && text.length <= 256) { "推荐文本无效" }
        require(source == null || source.length <= 64) { "推荐来源过长" }
    }
}

data class SuggestionState internal constructor(
    val items: List<SuggestionItem> = emptyList(),
    val selectedId: String? = null,
) {
    val selected: SuggestionItem? get() = items.firstOrNull { it.id == selectedId }
}

/** 维护候选栏推荐的内存状态，写回动作由输入法或宿主在用户点击后执行。 */
class SuggestionController {
    companion object {
        const val MAX_ITEMS = 6
    }

    private val mutableState = MutableStateFlow(SuggestionState())
    val state = mutableState.asStateFlow()

    @Synchronized
    fun replace(items: List<SuggestionItem>) {
        require(items.size <= MAX_ITEMS) { "推荐项过多" }
        require(items.map { it.id }.distinct().size == items.size) { "推荐标识重复" }
        val copied = Collections.unmodifiableList(items.toList())
        val selectedId = state.value.selectedId?.takeIf { id -> copied.any { it.id == id } }
            ?: copied.firstOrNull()?.id
        mutableState.value = SuggestionState(copied, selectedId)
    }

    @Synchronized
    fun select(id: String): Boolean {
        if (state.value.items.none { it.id == id }) return false
        mutableState.value = state.value.copy(selectedId = id)
        return true
    }

    @Synchronized
    fun remove(id: String) {
        replace(state.value.items.filterNot { it.id == id })
    }

    @Synchronized
    fun clear() = replace(emptyList())
}

