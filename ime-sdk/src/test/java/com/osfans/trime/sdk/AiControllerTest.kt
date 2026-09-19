package com.osfans.trime.sdk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
class AiControllerTest : StringSpec({
    "disabled route never calls a provider" {
        val controller = AiController()
        var calls = 0
        controller.setHostProvider(AiDraftProvider { calls += 1; "unused" })
        controller.route.value shouldBe AiProviderRoute.DISABLED

        controller.generate(AiDraftRequest("reply", "hello")).shouldBeNull()
        calls shouldBe 0
    }

    "built-in and host providers are selected explicitly" {
        val controller = AiController()
        controller.setBuiltInProvider(AiDraftProvider { "built-in" })
        controller.setHostProvider(AiDraftProvider { "host" })
        val request = AiDraftRequest("reply", "hello")

        controller.setRoute(AiProviderRoute.BUILT_IN)
        controller.generate(request) shouldBe "built-in"
        controller.setRoute(AiProviderRoute.HOST)
        controller.generate(request) shouldBe "host"
    }

    "draft generation returns a non-sending draft item" {
        val controller = AiController()
        controller.setBuiltInProvider(AiDraftProvider { "generated" })
        controller.setRoute(AiProviderRoute.BUILT_IN)

        val item = requireNotNull(controller.generateDraft(AiDraftRequest("reply", "hello"), "draft-id", "AI"))

        item.id shouldBe "draft-id"
        item.text shouldBe "generated"
    }

    "SDK-owned drafts regenerate through the built-in controller" {
        runBlocking {
        val drafts = DraftController()
        val controller = AiController()
        var count = 0
        controller.setBuiltInProvider(AiDraftProvider { "generated-${++count}" })
        controller.setRoute(AiProviderRoute.BUILT_IN)
        controller.attachDraftController(drafts)
        val item = requireNotNull(controller.generateDraft(AiDraftRequest("reply", "hello"), "retry-id", "AI"))
        drafts.replace(listOf(item))

        drafts.requestRegeneration(item) shouldBe true
        repeat(20) {
            if (drafts.state.value.selected?.text == "generated-2") return@runBlocking
            delay(10)
        }
            drafts.state.value.selected?.text shouldBe "generated-2"
        }
    }

    "built-in AI can publish short suggestions without creating a draft" {
        val controller = AiController()
        val suggestions = SuggestionController()
        controller.setBuiltInProvider(AiDraftProvider { "1. 好的\n- 我看到了\n好的" })
        controller.setRoute(AiProviderRoute.BUILT_IN)

        val result = controller.generateAndPublishSuggestions(
            AiDraftRequest("给出简短建议", "你好", maxOutputTokens = 96),
            suggestions,
            "quick",
        )

        result?.map { it.text } shouldBe listOf("好的", "我看到了")
        suggestions.state.value.items.map { it.id } shouldBe listOf("quick-0", "quick-1")
    }

    "a newer request cancels the older request in the same operation slot" {
        runBlocking {
            val firstCancelled = CompletableDeferred<Boolean>()
            val controller = AiController()
            var calls = 0
            controller.setBuiltInProvider(object : AiDraftStreamingProvider {
                override suspend fun generateStreaming(
                    request: AiDraftRequest,
                    onChunk: suspend (String) -> Unit,
                ): String {
                    calls += 1
                    if (calls == 1) {
                        try {
                            awaitCancellation()
                        } finally {
                            firstCancelled.complete(true)
                        }
                    }
                    onChunk("new-result")
                    return "new-result"
                }
            })
            controller.setRoute(AiProviderRoute.BUILT_IN)
            val first = launch {
                controller.generateDraftStreaming(
                    AiDraftRequest("first", "text"),
                    "first-id",
                    "first",
                    onChunk = {},
                    requestKey = "context",
                )
            }
            delay(30)
            val second = launch {
                controller.generateDraftStreaming(
                    AiDraftRequest("second", "text"),
                    "second-id",
                    "second",
                    onChunk = {},
                    requestKey = "context",
                )
            }
            second.join()
            firstCancelled.await()
            first.isCancelled shouldBe true
        }
    }
})
