package com.osfans.trime.sdk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DraftKeyboardHeightPolicyTest : StringSpec({
    "normal draft height keeps the themed keyboard height" {
        DraftKeyboardHeightPolicy.resolve(
            baseHeight = 256,
            bodyHeight = 60,
            normalBodyHeight = 60,
            minimumHeight = 48,
            maximumExtraHeight = 160,
        ) shouldBe 256
    }

    "collapsed draft leaves only the minimum keyboard height" {
        DraftKeyboardHeightPolicy.resolve(
            baseHeight = 256,
            bodyHeight = 0,
            normalBodyHeight = 60,
            minimumHeight = 48,
            maximumExtraHeight = 160,
        ) shouldBe 48
    }

    "expanded draft grows the keyboard but respects the extra-height cap" {
        DraftKeyboardHeightPolicy.resolve(
            baseHeight = 256,
            bodyHeight = 240,
            normalBodyHeight = 60,
            minimumHeight = 48,
            maximumExtraHeight = 160,
        ) shouldBe 416
    }
})

