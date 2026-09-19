package com.osfans.trime.sdk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class EditorCommandRouterTest : StringSpec({
    "commands are delivered to the active editor handler" {
        val router = EditorCommandRouter()
        val received = mutableListOf<EditorCommand>()
        val snapshot = EditorSnapshot("abc", 1, 2, sessionId = 1)
        router.attach(EditorCommandHandler { command, expected ->
            expected shouldBe snapshot
            received += command
            true
        })

        router.dispatch(EditorCommand.CommitText("你好"), snapshot) shouldBe true
        router.dispatch(EditorCommand.ReplaceSelection("世界"), snapshot) shouldBe true
        router.dispatch(EditorCommand.DeleteSelection, snapshot) shouldBe true
        router.dispatch(EditorCommand.SelectAll, snapshot) shouldBe true
        received shouldBe listOf(
            EditorCommand.CommitText("你好"),
            EditorCommand.ReplaceSelection("世界"),
            EditorCommand.DeleteSelection,
            EditorCommand.SelectAll,
        )
    }

    "commands are rejected after the editor handler is cleared" {
        val router = EditorCommandRouter()
        val snapshot = EditorSnapshot("abc", 1, 2, sessionId = 1)
        val handler = EditorCommandHandler { _, _ -> true }
        router.dispatch(EditorCommand.CommitText("x"), snapshot) shouldBe false
        router.attach(handler)
        router.dispatch(EditorCommand.CommitText("x"), snapshot) shouldBe true
        router.detach(handler)
        router.dispatch(EditorCommand.CommitText("x"), snapshot) shouldBe false
    }

    "old editor cleanup cannot detach a newer editor" {
        val router = EditorCommandRouter()
        val old = EditorCommandHandler { _, _ -> false }
        router.attach(old)
        router.attach(EditorCommandHandler { _, _ -> true })
        router.detach(old)
        router.dispatch(EditorCommand.SelectAll, EditorSnapshot("a", 0, 1, sessionId = 1)) shouldBe true
    }
})
