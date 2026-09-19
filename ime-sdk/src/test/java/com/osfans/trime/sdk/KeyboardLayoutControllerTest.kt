package com.osfans.trime.sdk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KeyboardLayoutControllerTest : StringSpec({
    "published layout id updates the public state" {
        val controller = KeyboardLayoutController()
        controller.publish("pinyin_t9")
        controller.current.value shouldBe KeyboardLayout.T9_PINYIN
        controller.publish("unknown")
        controller.current.value shouldBe null
    }

    "stale handler detach cannot clear a newer owner" {
        val controller = KeyboardLayoutController()
        val oldHandler = KeyboardLayoutRequestHandler { true }
        val newHandler = KeyboardLayoutRequestHandler { true }
        controller.attach(oldHandler)
        controller.attach(newHandler)
        controller.publish("symbols")
        controller.detach(oldHandler)
        controller.current.value shouldBe KeyboardLayout.SYMBOLS
    }

    "layout ids remain stable for host configuration" {
        KeyboardLayout.QWERTY.id shouldBe "qwerty"
        KeyboardLayout.T9_PINYIN.id shouldBe "pinyin_t9"
        KeyboardLayout.EDIT.id shouldBe "edit"
        KeyboardLayout.fromId("not-a-layout") shouldBe null
    }
})

