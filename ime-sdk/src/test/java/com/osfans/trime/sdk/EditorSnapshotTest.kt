package com.osfans.trime.sdk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class EditorSnapshotTest : StringSpec({
    "selection and composing ranges are clamped to the extracted text" {
        val snapshot = EditorSnapshot(
            text = "你好世界",
            selectionStart = -2,
            selectionEnd = 99,
            composingStart = 99,
            composingEnd = -4,
        )

        snapshot.safeSelectionStart shouldBe 0
        snapshot.safeSelectionEnd shouldBe 4
        snapshot.safeComposingStart shouldBe -1
        snapshot.safeComposingEnd shouldBe -1
        snapshot.hasTextBefore shouldBe false
        snapshot.hasTextAfter shouldBe false
        snapshot.hasSelection shouldBe false
    }

    "cursor state distinguishes empty, before-text and after-text fields" {
        EditorSnapshot("", 0, 0).hasTextBefore shouldBe false
        EditorSnapshot("abc", 0, 0).hasTextAfter shouldBe true
        EditorSnapshot("abc", 3, 3).hasTextBefore shouldBe true
        EditorSnapshot("abc", 1, 2).hasSelection shouldBe true
    }
    "reverse selections and partial extracts retain their coordinate semantics" {
        val snapshot = EditorSnapshot("abcd", 3, 1, textOffset = 100)
        snapshot.hasTextBefore shouldBe true
        snapshot.hasTextAfter shouldBe true
        snapshot.selectedText shouldBe "bc"
        snapshot.absoluteSelectionStart shouldBe 103
        snapshot.absoluteSelectionEnd shouldBe 101
        EditorSnapshot("", -1, -1).hasValidSelection shouldBe false
        EditorSnapshot("abc", 0, 0).safeComposingStart shouldBe -1
    }
})
