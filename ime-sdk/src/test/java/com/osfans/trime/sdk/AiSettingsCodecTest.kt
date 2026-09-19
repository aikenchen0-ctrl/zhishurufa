package com.osfans.trime.sdk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AiSettingsCodecTest : StringSpec({
    "round trips backend configuration without changing values" {
        val settings = AiSettings(
            AiProviderRoute.BUILT_IN,
            AiConfiguration.openAi("runtime-key", "https://gateway.example", "gpt-test"),
        )

        AiSettingsCodec.decode(AiSettingsCodec.encode(settings)) shouldBe settings
    }

    "empty and malformed payloads are treated as disabled" {
        AiSettingsCodec.decode("") shouldBe AiSettings()
        AiSettingsCodec.decode("not-json") shouldBe AiSettings()
    }
})
