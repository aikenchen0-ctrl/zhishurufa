package com.osfans.trime.sdk

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldContain

class GrokChatProtocolTest : StringSpec({
    "AI draft operations have stable prompts" {
        AiDraftOperation.REPLY.instruction shouldContain "回复"
        AiDraftOperation.REWRITE.instruction shouldContain "重写"
        AiDraftOperation.POLISH.instruction shouldContain "润色"
        AiDraftOperation.CONTINUE.instruction shouldContain "续写"
        AiDraftOperation.SUGGEST.instruction shouldContain "建议"
    }

    "normalizes an OpenAI compatible endpoint" {
        GrokChatProtocol.chatCompletionsUrl("https://api.cc2.cx") shouldBe "https://api.cc2.cx/v1/chat/completions"
        GrokChatProtocol.chatCompletionsUrl("https://api.cc2.cx/v1") shouldBe "https://api.cc2.cx/v1/chat/completions"
        GrokChatProtocol.chatCompletionsUrl("https://api.cc2.cx/v1/chat/completions") shouldBe "https://api.cc2.cx/v1/chat/completions"
    }

    "provides Grok and OpenAI built-in presets" {
        AiConfiguration.grok("key").backend shouldBe AiBackend.GROK
        AiConfiguration.openAi("key").backend shouldBe AiBackend.OPENAI
        AiConfiguration.openAi("key").endpoint shouldBe "https://api.openai.com"
        AiConfiguration.grok("secret-key").toString() shouldNotContain "secret-key"
    }

    "rejects insecure or malformed AI endpoints" {
        shouldThrow<IllegalArgumentException> { AiConfiguration.grok("key", "http://localhost") }
        shouldThrow<IllegalArgumentException> { AiConfiguration.grok("key", "not-an-url") }
        shouldThrow<IllegalArgumentException> { AiConfiguration.grok("key", "https://") }
        shouldThrow<IllegalArgumentException> { AiConfiguration.grok("key", "https://user:pass@example.com") }
        shouldThrow<IllegalArgumentException> { AiConfiguration.grok("key", "https://example.com?token=secret") }
    }

    "encodes a draft request without putting the API key in the body" {
        val request = AiDraftRequest("润色语气", "请尽快回复我")
        val body = GrokChatProtocol.encode("grok-4.6", request)

        body shouldContain "grok-4.6"
        body shouldContain "请尽快回复我"
        body shouldContain "润色语气"
        body shouldNotContain "Bearer"
    }

    "extracts text and rejects an empty response" {
        GrokChatProtocol.decode("{\"choices\":[{\"message\":{\"content\":\"你好\"}}]}") shouldBe "你好"
        shouldThrow<AiClientException> { GrokChatProtocol.decode("{\"choices\":[]}") }
    }
})
