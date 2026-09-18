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
        snapshot.safeComposingStart shouldBe 4
        snapshot.safeComposingEnd shouldBe 0
        snapshot.hasTextBefore shouldBe false
        snapshot.hasTextAfter shouldBe false
        snapshot.hasSelection shouldBe true
    }

    "cursor state distinguishes empty, before-text and after-text fields" {
        EditorSnapshot("", 0, 0).hasTextBefore shouldBe false
        EditorSnapshot("abc", 0, 0).hasTextAfter shouldBe true
        EditorSnapshot("abc", 3, 3).hasTextBefore shouldBe true
        EditorSnapshot("abc", 1, 2).hasSelection shouldBe true
    }
})
