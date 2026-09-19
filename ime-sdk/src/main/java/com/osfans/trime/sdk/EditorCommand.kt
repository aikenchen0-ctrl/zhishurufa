package com.osfans.trime.sdk

/** 宿主对当前编辑器发出的纯文本编辑命令，不暴露 Android InputConnection。 */
sealed interface EditorCommand {
    data class CommitText(val text: String) : EditorCommand
    data class ReplaceSelection(val text: String) : EditorCommand
    data object DeleteSelection : EditorCommand
    data object SelectAll : EditorCommand
}

internal fun interface EditorCommandHandler {
    fun handle(command: EditorCommand, expected: EditorSnapshot): Boolean
    fun snapshot(): EditorSnapshot? = null
    fun contextEnabledChanged() = Unit
}

internal class EditorCommandRouter {
    @Volatile
    private var handler: EditorCommandHandler? = null

    fun attach(handler: EditorCommandHandler) {
        this.handler = handler
    }

    fun detach(handler: EditorCommandHandler) {
        if (this.handler === handler) this.handler = null
    }

    fun snapshot(): EditorSnapshot? = try { handler?.snapshot() } catch (_: Exception) { null }

    fun contextEnabledChanged() {
        try { handler?.contextEnabledChanged() } catch (_: Exception) { }
    }

    fun dispatch(command: EditorCommand, expected: EditorSnapshot): Boolean = try {
        handler?.handle(command, expected) ?: false
    } catch (_: Exception) {
        false
    }
}
