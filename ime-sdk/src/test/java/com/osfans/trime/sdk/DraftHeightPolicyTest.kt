package com.osfans.trime.sdk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DraftHeightPolicyTest : StringSpec({
    "dragging down past the threshold collapses the detail body" {
        DraftHeightPolicy.resolve(startHeight = 60, distance = -40, maximum = 160) shouldBe
            DraftHeight(expanded = false, bodyHeight = 0)
    }

    "dragging up from collapsed uses the minimum expanded height" {
        DraftHeightPolicy.resolve(startHeight = 0, distance = 30, maximum = 160) shouldBe
            DraftHeight(expanded = true, bodyHeight = 48)
    }

    "dragging beyond the screen limit is capped" {
        DraftHeightPolicy.resolve(startHeight = 120, distance = 100, maximum = 160) shouldBe
            DraftHeight(expanded = true, bodyHeight = 160)
    }
})

