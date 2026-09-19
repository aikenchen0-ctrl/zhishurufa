package com.osfans.trime.sdk

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DraftControllerTest : StringSpec({
    val first = DraftItem("first", "Reply A", listOf(DraftSegment("old", DraftChange.REMOVED), DraftSegment("new", DraftChange.ADDED)))
    val second = DraftItem("second", "Reply B", listOf(DraftSegment("other")))

    "deleted spans are never written into the editor" {
        first.text shouldBe "new"
        first.originalText shouldBe "old"
    }
    "drafts stay hidden without data and only one attached surface can own display" {
        val controller = DraftController()
        val host = controller.attach(DraftSurface.HOST)
        controller.state.value.owner shouldBe null
        controller.replace(listOf(first, second))
        controller.state.value.owner shouldBe host
        val ime = controller.attach(DraftSurface.IME)
        controller.state.value.owner shouldBe host
        controller.setImeState(visible = true, allowed = true)
        controller.state.value.owner shouldBe ime
        controller.setImeState(visible = false, allowed = true)
        controller.state.value.owner shouldBe host
    }
    "blocked fields hide all surfaces and old view cleanup cannot detach a new view" {
        val controller = DraftController()
        controller.replace(listOf(first))
        controller.attach(DraftSurface.HOST)
        val old = controller.attach(DraftSurface.IME)
        val current = controller.attach(DraftSurface.IME)
        controller.setImeState(true, true)
        controller.detach(old)
        controller.state.value.owner shouldBe current
        controller.setImeState(true, false)
        controller.state.value.owner shouldBe null
    }
    "selection and clearing are deterministic without changing editor text" {
        val controller = DraftController()
        controller.replace(listOf(first, second))
        controller.state.value.selected shouldBe first
        controller.next()
        controller.state.value.selected shouldBe second
        controller.remove(second.id)
        controller.state.value.selected shouldBe first
        controller.clear()
        controller.state.value.selected shouldBe null
    }
    "duplicate ids and excessive drafts fail without changing the previous state" {
        val controller = DraftController()
        controller.replace(listOf(first))
        shouldThrow<IllegalArgumentException> { controller.replace(listOf(second, second)) }
        controller.state.value.selected shouldBe first
        shouldThrow<IllegalArgumentException> { DraftItem("", "", emptyList()) }
    }
    "restored or changed drafts never inherit a live editor binding" {
        val controller = DraftController()
        val snapshot = EditorSnapshot("", 0, 0, sessionId = 8)
        controller.replace(listOf(first))
        controller.bind(first.id, snapshot) shouldBe true
        controller.binding(first.id) shouldBe snapshot
        controller.replace(listOf(first.copy(title = "changed")))
        controller.binding(first.id) shouldBe null
    }
    "a generated draft can be atomically bound to its source snapshot" {
        val controller = DraftController()
        val snapshot = EditorSnapshot("hello", 5, 5, sessionId = 9)

        controller.replaceAndBind(listOf(first), first.id, snapshot) shouldBe true
        controller.binding(first.id) shouldBe snapshot
        controller.replaceAndBind(listOf(second), first.id, snapshot) shouldBe false
        controller.binding(first.id) shouldBe snapshot
    }
    "regenerating a draft keeps its source binding for stale snapshot validation" {
        val controller = DraftController()
        val snapshot = EditorSnapshot("hello", 5, 5, sessionId = 10)
        controller.replaceAndBind(listOf(first), first.id, snapshot)

        controller.update(first, first.copy(segments = listOf(DraftSegment("new result")))) shouldBe true
        controller.binding(first.id) shouldBe snapshot
    }
    "upserting one draft keeps bindings for other drafts" {
        val controller = DraftController()
        val firstSnapshot = EditorSnapshot("first", 5, 5, sessionId = 11)
        val secondSnapshot = EditorSnapshot("second", 6, 6, sessionId = 12)
        controller.replace(listOf(first, second))
        controller.bind(first.id, firstSnapshot) shouldBe true
        controller.bind(second.id, secondSnapshot) shouldBe true

        controller.upsertAndBind(first.copy(segments = listOf(DraftSegment("updated"))), firstSnapshot) shouldBe true

        controller.binding(first.id) shouldBe firstSnapshot
        controller.binding(second.id) shouldBe secondSnapshot
        controller.state.value.items.map { it.id } shouldBe listOf("first", "second")
    }
})
