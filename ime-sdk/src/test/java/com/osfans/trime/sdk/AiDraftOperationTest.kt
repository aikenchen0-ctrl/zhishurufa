package com.osfans.trime.sdk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AiDraftOperationTest : StringSpec({
    "empty input exposes reply and continue actions" {
        AiDraftOperation.contextActions(EditorSnapshot("", 0, 0, sessionId = 1)) shouldBe
            listOf(AiDraftOperation.REPLY, AiDraftOperation.CONTINUE)
    }

    "text without selection exposes rewrite polish and continue" {
        AiDraftOperation.contextActions(EditorSnapshot("已有内容", 4, 4, sessionId = 2)) shouldBe
            listOf(AiDraftOperation.REWRITE, AiDraftOperation.POLISH, AiDraftOperation.CONTINUE)
    }

    "selected text exposes only operations that replace or refine it" {
        AiDraftOperation.contextActions(EditorSnapshot("已有内容", 0, 2, sessionId = 3)) shouldBe
            listOf(AiDraftOperation.REWRITE, AiDraftOperation.POLISH)
    }
})

