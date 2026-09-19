/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RimeConfigLifetimeTest : StringSpec({
    "missing config keeps nullable scalar defaults" {
        val config = missingConfig()

        config.getInt("missing") shouldBe null
        config.getString("missing") shouldBe null
    }

    "missing config returns an empty list without invoking the reader" {
        val config = missingConfig()
        var called = false

        config.getList("missing") {
            called = true
            "value"
        } shouldBe emptyList()
        called shouldBe false
    }

    "missing config accepts writes without entering JNI" {
        missingConfig().setBool("missing", true)
    }

    "closing a missing config repeatedly is safe without entering JNI" {
        val config = missingConfig()

        repeat(3) { config.close() }
    }

    "closed config rejects every scalar read and write" {
        val config = missingConfig()
        config.close()

        shouldThrow<IllegalStateException> { config.getInt("missing") }
        shouldThrow<IllegalStateException> { config.getString("missing") }
        shouldThrow<IllegalStateException> { config.setBool("missing", true) }
    }

    "closed config rejects list reads before invoking the reader" {
        val config = missingConfig()
        config.close()
        var called = false

        shouldThrow<IllegalStateException> {
            config.getList("missing") {
                called = true
                "value"
            }
        }
        called shouldBe false
    }
})

private fun missingConfig(): RimeConfig =
    RimeConfig::class.java.getDeclaredConstructor(Long::class.javaPrimitiveType).run {
        isAccessible = true
        newInstance(0L)
    }
