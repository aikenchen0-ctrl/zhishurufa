package com.osfans.trime.ime.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InputFieldPolicyTest : StringSpec({
    "ordinary text does not force a keyboard mode" {
        InputFieldKeyboard.resolve(InputType.TYPE_CLASS_TEXT, 0) shouldBe InputFieldKeyboard.TEXT
    }
    "numeric and phone fields use separate layouts" {
        InputFieldKeyboard.resolve(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, 0) shouldBe InputFieldKeyboard.NUMBER
        InputFieldKeyboard.resolve(InputType.TYPE_CLASS_PHONE, 0) shouldBe InputFieldKeyboard.PHONE
        InputFieldKeyboard.resolve(InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE, 0) shouldBe InputFieldKeyboard.DATETIME
        InputFieldKeyboard.resolve(InputType.TYPE_CLASS_NUMBER, EditorInfo.IME_FLAG_FORCE_ASCII) shouldBe InputFieldKeyboard.NUMBER
        InputFieldKeyboard.resolve(InputType.TYPE_CLASS_PHONE, EditorInfo.IME_FLAG_FORCE_ASCII) shouldBe InputFieldKeyboard.PHONE
    }
    "email and password need a full ASCII keyboard even after numeric entry" {
        InputFieldKeyboard.resolve(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 0) shouldBe InputFieldKeyboard.EMAIL
        InputFieldKeyboard.resolve(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, 0) shouldBe InputFieldKeyboard.EMAIL
        InputFieldKeyboard.resolve(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, 0) shouldBe InputFieldKeyboard.ASCII
        InputFieldKeyboard.resolve(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, 0) shouldBe InputFieldKeyboard.ASCII
        InputFieldKeyboard.resolve(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_FORCE_ASCII) shouldBe InputFieldKeyboard.ASCII
        InputFieldKeyboard.resolve(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, 0) shouldBe InputFieldKeyboard.ASCII
    }
    "custom themes fall back to an existing full keyboard" {
        InputFieldKeyboard.EMAIL.layout(setOf("default", "number", "letter"), "default") shouldBe "letter"
        InputFieldKeyboard.ASCII.layout(setOf("default", "number"), "default") shouldBe "default"
        InputFieldKeyboard.PHONE.layout(setOf("default", "number"), "default") shouldBe "number"
        InputFieldKeyboard.DATETIME.layout(setOf("default", "number", "datetime"), "default") shouldBe "datetime"
        InputFieldKeyboard.DATETIME.layout(setOf("default", "number"), "default") shouldBe "number"
    }
    "multiple temporary fields preserve the first text state" {
        val state = InputFieldSession()
        val original = TextInputState("pinyin_t9", "pinyin_t9", false)
        state.enter(InputFieldKeyboard.NUMBER, original) shouldBe null
        state.enter(InputFieldKeyboard.EMAIL, TextInputState("number", "pinyin_t9", true)) shouldBe null
        state.enter(InputFieldKeyboard.ASCII, TextInputState("email", "pinyin_t9", true)) shouldBe null
        state.enter(InputFieldKeyboard.TEXT, TextInputState("letter", "pinyin_t9", true)) shouldBe original
        state.enter(InputFieldKeyboard.TEXT, original) shouldBe null
    }
    "ending an input connection forgets its temporary state" {
        val state = InputFieldSession()
        state.enter(InputFieldKeyboard.NUMBER, TextInputState("number", "pinyin_t9", false))
        state.clear()
        state.enter(InputFieldKeyboard.TEXT, TextInputState("default", "luna_pinyin_simp", false)) shouldBe null
    }
    "an English text preference survives a temporary field" {
        val state = InputFieldSession()
        val original = TextInputState("letter", "luna_pinyin_simp", true)
        state.enter(InputFieldKeyboard.PHONE, original)
        state.enter(InputFieldKeyboard.TEXT, TextInputState("phone", "luna_pinyin_simp", true)) shouldBe original
    }
})
