package com.osfans.trime.data.theme

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.io.File
import com.osfans.trime.util.yaml.mapping

class AiToolbarThemeTest : StringSpec({
    val directory = File("src/main/assets/shared")

    fun theme(name: String): Theme {
        val node = com.osfans.trime.util.yaml.Yaml.parseToYamlNode(directory.resolve(name).readText())
        return Theme.decode(node.mapping!!)
    }

    listOf("trime.yaml", "tongwenfeng.trime.yaml").forEach { file ->
        "${file} exposes the SDK AI toolbar action" {
            val theme = theme(file)
            theme.presetKeys.keys shouldContain "Ai_draft"
            theme.presetKeys.keys shouldContain "Ai_menu"
            theme.toolBar.buttons.any { it.action == "Ai_draft" } shouldBe true
            theme.toolBar.buttons.first { it.action == "Ai_draft" }.longPressAction shouldBe "Ai_menu"
        }
    }
})
