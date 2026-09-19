package com.osfans.trime.sdk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class AiStreamingTest : StringSpec({
    "streaming delta parser extracts content and ignores finish markers" {
        GrokChatProtocol.decodeDelta("{\"choices\":[{\"delta\":{\"content\":\"你\"}}]}") shouldBe "你"
        GrokChatProtocol.decodeDelta("{\"choices\":[{\"delta\":{\"content\":\"好\"}}]}") shouldBe "好"
        GrokChatProtocol.decodeDelta("{\"choices\":[{\"delta\":{}}]}") shouldBe ""
    }

    "request body enables streaming and bounds output" {
        val body = GrokChatProtocol.encode("grok-4.6", AiDraftRequest("续写", "你好", maxOutputTokens = 128), stream = true)
        body shouldContain "\"stream\":true"
        body shouldContain "\"max_tokens\":128"
    }
})
