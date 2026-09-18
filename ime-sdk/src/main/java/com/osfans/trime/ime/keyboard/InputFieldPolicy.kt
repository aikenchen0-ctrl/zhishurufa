// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * 根据宿主编辑器声明选择临时键盘。该策略不触碰 Rime 状态，便于 SDK 宿主复用和单元测试。
 */
internal enum class InputFieldKeyboard {
    TEXT,
    NUMBER,
    PHONE,
    DATETIME,
    EMAIL,
    ASCII;

    val isTemporary: Boolean
        get() = this != TEXT

    /** 在主题缺少专用布局时逐级回退到可用布局。 */
    fun layout(availableIds: Set<String>, fallback: String): String = when (this) {
        TEXT -> fallback
        NUMBER -> if ("number" in availableIds) "number" else fallback
        PHONE -> when {
            "phone" in availableIds -> "phone"
            "number" in availableIds -> "number"
            else -> fallback
        }
        DATETIME -> when {
            "datetime" in availableIds -> "datetime"
            "number" in availableIds -> "number"
            else -> fallback
        }
        EMAIL, ASCII -> when {
            "letter" in availableIds -> "letter"
            else -> fallback
        }
    }

    companion object {
        fun resolve(inputType: Int, imeOptions: Int): InputFieldKeyboard {
            return when (inputType and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_NUMBER -> NUMBER
                InputType.TYPE_CLASS_DATETIME -> DATETIME
                InputType.TYPE_CLASS_PHONE -> PHONE
                InputType.TYPE_CLASS_TEXT -> when (inputType and InputType.TYPE_MASK_VARIATION) {
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> EMAIL
                    InputType.TYPE_TEXT_VARIATION_URI -> ASCII
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> ASCII
                    else -> if (imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII != 0) ASCII else TEXT
                }
                else -> TEXT
            }
        }
    }
}

internal data class TextInputState(
    val keyboardId: String,
    val schemaId: String,
    val asciiMode: Boolean,
)

/**
 * 保存进入第一个临时字段前的状态。连续切换数字、邮箱等字段不会覆盖原始中文状态。
 */
internal class InputFieldSession {
    private var savedTextState: TextInputState? = null

    @Synchronized
    fun enter(field: InputFieldKeyboard, current: TextInputState): TextInputState? {
        if (field.isTemporary) {
            if (savedTextState == null) savedTextState = current
            return null
        }
        return savedTextState.also { savedTextState = null }
    }

    @Synchronized
    fun clear() {
        savedTextState = null
    }

}
