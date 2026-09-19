package com.osfans.trime.sdk

import android.text.InputType
import android.view.inputmethod.EditorInfo
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class EditorAccessPolicyTest : StringSpec({
    "all password variants and private fields deny context access" {
        listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            InputType.TYPE_NULL,
        ).forEach { EditorAccessPolicy.canAccess(it, 0) shouldBe false }
        EditorAccessPolicy.canAccess(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) shouldBe false
        EditorAccessPolicy.canAccess(InputType.TYPE_CLASS_TEXT, 0) shouldBe true
    }

    "writeback rejects stale sessions edits selections and composition" {
        val original = EditorSnapshot("abc", 1, 2, sessionId = 10)
        EditorAccessPolicy.canEdit(original, original, EditorCommand.ReplaceSelection("x")) shouldBe true
        listOf(
            original.copy(sessionId = 11), original.copy(text = "def"),
            original.copy(selectionStart = 0), original.copy(textOffset = 10),
            original.copy(composingStart = 0, composingEnd = 1),
        ).forEach { EditorAccessPolicy.canEdit(original, it, EditorCommand.ReplaceSelection("x")) shouldBe false }
    }

    "selection commands never insert without a selection" {
        val cursor = EditorSnapshot("abc", 1, 1, sessionId = 10)
        EditorAccessPolicy.canEdit(cursor, cursor, EditorCommand.ReplaceSelection("x")) shouldBe false
        EditorAccessPolicy.canEdit(cursor, cursor, EditorCommand.DeleteSelection) shouldBe false
        EditorAccessPolicy.canEdit(cursor, cursor, EditorCommand.CommitText("x")) shouldBe true
        EditorAccessPolicy.canEdit(cursor, cursor, EditorCommand.CommitText("")) shouldBe false
        EditorAccessPolicy.canEdit(cursor, cursor.copy(selectionStart = -1), EditorCommand.SelectAll) shouldBe false
    }
})
