package com.osfans.trime.sdk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InputSchemeContractTest : StringSpec({
    "scheme identifiers expose stable non-empty values" {
        InputScheme.PINYIN.id shouldBe "luna_pinyin_simp"
        InputScheme.WUBI86.id shouldBe "wubi86"
        InputScheme.STROKE.id shouldBe "stroke"
    }

    "unknown scheme names are rejected before reaching the engine" {
        InputScheme.fromId("unknown") shouldBe null
        InputScheme.fromId("luna_pinyin_simp") shouldBe InputScheme.PINYIN
    }
})
