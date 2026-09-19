package com.osfans.trime.sdk

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class SuggestionControllerTest : StringSpec({
    "suggestions keep selection when a replacement contains the selected id" {
        val controller = SuggestionController()
        controller.replace(
            listOf(
                SuggestionItem("reply", "好的"),
                SuggestionItem("later", "晚点回复"),
            ),
        )
        controller.select("later") shouldBe true

        controller.replace(
            listOf(
                SuggestionItem("later", "晚点联系"),
                SuggestionItem("new", "我看到了"),
            ),
        )

        controller.state.value.selected?.text shouldBe "晚点联系"
    }

    "replacing suggestions drops a missing selection and empty state clears it" {
        val controller = SuggestionController()
        controller.replace(listOf(SuggestionItem("old", "旧建议")))
        controller.replace(listOf(SuggestionItem("new", "新建议")))
        controller.state.value.selected?.id shouldBe "new"
        controller.clear()
        controller.state.value.selected.shouldBeNull()
        controller.state.value.items shouldBe emptyList()
    }

    "invalid text, duplicate ids and more than six items are rejected" {
        val controller = SuggestionController()
        shouldThrow<IllegalArgumentException> { SuggestionItem("", "建议") }
        shouldThrow<IllegalArgumentException> { SuggestionItem("id", " ") }
        shouldThrow<IllegalArgumentException> {
            controller.replace(listOf(SuggestionItem("same", "一"), SuggestionItem("same", "二")))
        }
        shouldThrow<IllegalArgumentException> {
            controller.replace((0 until 7).map { SuggestionItem("id-$it", "建议$it") })
        }
    }
})

