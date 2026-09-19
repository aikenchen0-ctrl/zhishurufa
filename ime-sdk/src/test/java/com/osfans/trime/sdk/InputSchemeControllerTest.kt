package com.osfans.trime.sdk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InputSchemeControllerTest : StringSpec({
    "published schema id updates the public state" {
        val controller = InputSchemeController()
        controller.publish("wubi86")
        controller.current.value shouldBe InputScheme.WUBI86
        controller.publish("unknown")
        controller.current.value shouldBe null
    }

    "detaching a handler clears state and rejects requests" {
        val controller = InputSchemeController()
        val handler = InputSchemeRequestHandler { true }
        controller.attach(handler)
        controller.detach(handler)
        controller.current.value shouldBe null
    }

    "stale handler detach cannot clear a newer owner" {
        val controller = InputSchemeController()
        val oldHandler = InputSchemeRequestHandler { true }
        val newHandler = InputSchemeRequestHandler { true }
        controller.attach(oldHandler)
        controller.attach(newHandler)
        controller.publish("wubi86")
        controller.detach(oldHandler)
        controller.current.value shouldBe InputScheme.WUBI86
    }
})
