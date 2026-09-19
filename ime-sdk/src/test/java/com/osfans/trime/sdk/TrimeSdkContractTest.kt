package com.osfans.trime.sdk

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TrimeSdkContractTest : StringSpec({
    "service component identifies the host package and stable SDK service" {
        TrimeSdk.serviceComponent("com.example.host") shouldBe
            "com.example.host/com.osfans.trime.ime.core.TrimeInputMethodService"
    }

    "service component rejects an empty package name" {
        shouldThrow<IllegalArgumentException> { TrimeSdk.serviceComponent("") }
    }

    "service component preserves the original standalone Android identifier" {
        TrimeSdk.serviceComponent("com.osfans.trime") shouldBe
            "com.osfans.trime/.ime.core.TrimeInputMethodService"
    }

    "initialization runs once for the same application" {
        val state = SdkInitialization<Any>()
        val application = Any()
        var calls = 0
        repeat(2) { state.initialize(application) { calls++ } }
        calls shouldBe 1
        state.value shouldBe application
        state.isInitialized shouldBe true
    }

    "initialization cannot replace the owning application" {
        val state = SdkInitialization<Any>()
        val first = Any()
        state.initialize(first) {}
        shouldThrow<IllegalStateException> { state.initialize(Any()) {} }
        state.value shouldBe first
    }

    "failed initialization propagates the original error without retrying partial setup" {
        val state = SdkInitialization<Any>()
        val application = Any()
        val failure = IllegalStateException("initialization failed")
        var calls = 0
        repeat(2) {
            shouldThrow<IllegalStateException> {
                state.initialize(application) {
                    calls++
                    throw failure
                }
            } shouldBe failure
        }
        calls shouldBe 1
        state.isInitialized shouldBe false
        shouldThrow<IllegalStateException> { state.value } shouldBe failure
    }

    "context access before initialization fails explicitly" {
        shouldThrow<IllegalStateException> { SdkInitialization<Any>().value }
    }

    "public initialization guard reports the required startup order" {
        shouldThrow<IllegalStateException> { TrimeSdk.requireInitialized() }
    }

    "recursive initialization fails instead of running setup twice" {
        val state = SdkInitialization<Any>()
        val application = Any()
        var calls = 0
        shouldThrow<IllegalStateException> {
            state.initialize(application) {
                calls++
                state.initialize(application) { calls++ }
            }
        }
        calls shouldBe 1
        state.isInitialized shouldBe false
    }
})
