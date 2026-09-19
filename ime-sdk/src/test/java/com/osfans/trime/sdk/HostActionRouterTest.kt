package com.osfans.trime.sdk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class HostActionRouterTest : StringSpec({
    "unregistered actions are not handled" {
        HostActionRouter().dispatch("draft.open", "") shouldBe false
    }
    "the host receives only the requested action and payload" {
        val router = HostActionRouter()
        var received = ""
        router.handler = ImeHostActionHandler { action, payload ->
            received = "$action:$payload"
            true
        }
        router.dispatch("draft.open", "selected") shouldBe true
        received shouldBe "draft.open:selected"
    }
    "unregistering a handler releases the route" {
        val router = HostActionRouter()
        router.handler = ImeHostActionHandler { _, _ -> true }
        router.handler = null
        router.dispatch("draft.open", "") shouldBe false
    }
    "a host exception does not interrupt normal keyboard input" {
        val router = HostActionRouter()
        router.handler = ImeHostActionHandler { _, _ -> error("host failure") }
        router.dispatch("draft.open", "") shouldBe false
    }
    "empty action names never invoke the host" {
        val router = HostActionRouter()
        var calls = 0
        router.handler = ImeHostActionHandler { _, _ ->
            calls++
            true
        }
        router.dispatch("", "") shouldBe false
        calls shouldBe 0
    }
})
