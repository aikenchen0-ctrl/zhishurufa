package com.osfans.trime.sdk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/** 数据由宿主推送；控制器只保存内存状态，展示权与编辑器绑定都不会持久化。 */
class DraftController {
    private val mutableState = MutableStateFlow(DraftState())
    val state = mutableState.asStateFlow()
    private val surfaces = linkedMapOf<Long, DraftSurface>()
    private val bindings = mutableMapOf<String, EditorSnapshot>()
    private var nextOwner = 0L
    private var imeVisible = false
    private var imeAllowed = false
    @Volatile var actionHandler: DraftActionHandler? = null
        set(value) {
            synchronized(this) {
                field = value
                mutableState.value = state.value.copy(canRegenerate = value != null)
            }
        }

    @Synchronized
    fun replace(items: List<DraftItem>) {
        require(items.size <= 50 && items.map { it.id }.distinct().size == items.size) { "草稿过多或标识重复" }
        val copied = Collections.unmodifiableList(items.map { it.copy(segments = Collections.unmodifiableList(it.segments.toList())) })
        bindings.clear()
        val selected = state.value.selectedId?.takeIf { id -> copied.any { it.id == id } } ?: copied.firstOrNull()?.id
        mutableState.value = DraftState(copied, selected, owner(copied), actionHandler != null)
    }

    internal fun attachActionHandler(handler: DraftActionHandler) {
        actionHandler = handler
    }

    @Synchronized
    fun update(expected: DraftItem, replacement: DraftItem): Boolean {
        if (expected.id != replacement.id || state.value.items.none { it == expected }) return false
        val saved = bindings.toMap()
        replace(state.value.items.map { if (it.id == expected.id) replacement else it })
        bindings.putAll(saved.filterKeys { key -> state.value.items.any { it.id == key } })
        return true
    }

    /** 在生成结果进入状态时同时关联来源快照，避免确认按钮出现竞态窗口。 */
    @Synchronized
    fun replaceAndBind(items: List<DraftItem>, draftId: String, snapshot: EditorSnapshot): Boolean {
        if (items.none { it.id == draftId }) return false
        if (snapshot.sessionId <= 0 || !snapshot.hasValidSelection || snapshot.hasComposition) return false
        replace(items)
        bindings[draftId] = snapshot
        return true
    }

    /** 只替换目标草稿并保留其他草稿的输入框绑定。 */
    @Synchronized
    fun upsertAndBind(item: DraftItem, snapshot: EditorSnapshot): Boolean {
        if (snapshot.sessionId <= 0 || !snapshot.hasValidSelection || snapshot.hasComposition) return false
        val saved = bindings.toMap()
        val items = if (state.value.items.any { it.id == item.id }) {
            state.value.items.map { existing -> if (existing.id == item.id) item else existing }
        } else {
            state.value.items + item
        }
        replace(items)
        bindings.putAll(saved.filterKeys { key -> state.value.items.any { it.id == key } })
        bindings[item.id] = snapshot
        return true
    }

    @Synchronized fun select(id: String): Boolean {
        if (state.value.items.none { it.id == id }) return false
        mutableState.value = state.value.copy(selectedId = id)
        return true
    }

    @Synchronized fun next() {
        val items = state.value.items
        if (items.isNotEmpty()) select(items[(items.indexOfFirst { it.id == state.value.selectedId } + 1) % items.size].id)
    }

    @Synchronized fun remove(id: String) {
        val saved = bindings.toMap()
        replace(state.value.items.filterNot { it.id == id })
        bindings.putAll(saved.filterKeys { it != id })
    }
    fun clear() = replace(emptyList())

    @Synchronized fun bind(id: String, snapshot: EditorSnapshot): Boolean {
        if (snapshot.sessionId <= 0 || !snapshot.hasValidSelection || snapshot.hasComposition || state.value.items.none { it.id == id }) return false
        bindings[id] = snapshot
        return true
    }
    @Synchronized fun binding(id: String): EditorSnapshot? = bindings[id]
    @Synchronized fun clearBindings() { bindings.clear() }

    fun requestRegeneration(item: DraftItem): Boolean {
        val handler = actionHandler ?: return false
        if (state.value.items.none { it == item }) return false
        return try { handler.regenerate(item); true } catch (_: Exception) { false }
    }

    @Synchronized internal fun attach(surface: DraftSurface): Long {
        val token = ++nextOwner
        surfaces[token] = surface
        refreshOwner()
        return token
    }
    @Synchronized internal fun detach(token: Long) { surfaces.remove(token); refreshOwner() }
    @Synchronized internal fun setImeState(visible: Boolean, allowed: Boolean) {
        imeVisible = visible
        imeAllowed = allowed
        refreshOwner()
    }
    private fun owner(items: List<DraftItem>): Long? {
        if (items.isEmpty() || imeVisible && !imeAllowed) return null
        val target = if (imeVisible) DraftSurface.IME else DraftSurface.HOST
        return surfaces.entries.lastOrNull { it.value == target }?.key
    }
    private fun refreshOwner() { mutableState.value = state.value.copy(owner = owner(state.value.items)) }
}
