package com.osfans.trime.sdk

import android.text.InputType
import android.view.inputmethod.EditorInfo

internal object EditorAccessPolicy {
    const val MAX_TEXT_LENGTH = 16_384

    fun canAccess(inputType: Int, imeOptions: Int): Boolean {
        if (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation !in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            )
            InputType.TYPE_CLASS_NUMBER -> variation != InputType.TYPE_NUMBER_VARIATION_PASSWORD
            InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
            else -> false
        }
    }

    fun canEdit(expected: EditorSnapshot, current: EditorSnapshot, command: EditorCommand): Boolean {
        if (expected.sessionId <= 0 || expected.sessionId != current.sessionId) return false
        if (!expected.hasValidSelection || !current.hasValidSelection) return false
        if (expected.hasComposition || current.hasComposition) return false
        if (expected.text != current.text || expected.textOffset != current.textOffset ||
            expected.selectionStart != current.selectionStart || expected.selectionEnd != current.selectionEnd) return false
        return when (command) {
            is EditorCommand.CommitText -> command.text.length in 1..MAX_TEXT_LENGTH
            is EditorCommand.ReplaceSelection -> current.hasSelection && command.text.length <= MAX_TEXT_LENGTH
            EditorCommand.DeleteSelection -> current.hasSelection
            EditorCommand.SelectAll -> true
        }
    }
}
