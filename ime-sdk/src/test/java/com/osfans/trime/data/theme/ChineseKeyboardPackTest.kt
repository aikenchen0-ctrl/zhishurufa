package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainAll
import java.io.File

class ChineseKeyboardPackTest : StringSpec({
    val directory = File("src/main/assets/shared")
    fun load(id: String): Node = Yaml.parseToYamlNode(directory.resolve("$id.yaml").readText())
    fun theme(): Theme = Theme.decode(ThemeDslExpander.expand("zhishurufa.trime", load("zhishurufa.trime"), ::load).mapping!!)
    fun clicks(id: String): List<String> = theme().presetKeyboards.getValue(id).keys.mapNotNull {
        (it.behaviors[KeyBehavior.CLICK] as? KeyActionToken.Plain)?.token
    }
    fun inlineCommits(id: String): List<String> = theme().presetKeyboards.getValue(id).keys.mapNotNull {
        (it.behaviors[KeyBehavior.CLICK] as? KeyActionToken.Inline)?.token?.commit
    }

    "theme exposes every required keyboard family" {
        theme().presetKeyboards.keys.shouldContainAll("default", "qwerty", "letter", "pinyin_t9", "number", "phone", "datetime", "symbols", "edit", "wubi86", "stroke")
        clicks("qwerty").shouldContainAll(('a'..'z').map(Char::toString))
        clicks("pinyin_t9").shouldContainAll((2..9).map { "T9_$it" })
        theme().presetKeyboards.getValue("pinyin_t9").asciiKeyboard shouldBe "letter"
        inlineCommits("phone").shouldContainAll("+", "*", "#")
        inlineCommits("datetime").shouldContainAll("/", "-", ":")
        inlineCommits("symbols").shouldContainAll("，", "。", "？", "！")
    }

    "editing commands and return route are always available" {
        theme().presetKeyboards.getValue("edit").preserveAsciiMode shouldBe true
        clicks("edit").shouldContainAll("Left", "Right", "Up", "Down", "Home", "End", "select_all", "copy", "cut", "paste", "BackSpace", "Keyboard_default")
        listOf("number", "symbols", "edit").forEach {
            clicks(it).shouldContainAll("Keyboard_default")
        }
    }

    "semicolon double pinyin layouts retain a real encoding key" {
        listOf("double_pinyin_mspy", "double_pinyin_sogou", "double_pinyin_ziguang").forEach {
            clicks(it).shouldContainAll("semicolon")
        }
        theme().presetKeys.getValue("semicolon").send shouldBe "semicolon"
    }

    "fresh installs enable every bundled Chinese input family" {
        val ids = load("builtin.default").mapping!!["patch"]!!.mapping!!["schema_list"]!!.sequence!!.map { it.mapping!!["schema"]!!.string!! }
        ids.shouldContainAll("luna_pinyin_simp", "pinyin_t9", "double_pinyin", "double_pinyin_flypy", "double_pinyin_mspy", "double_pinyin_abc", "double_pinyin_sogou", "double_pinyin_ziguang", "wubi86", "stroke")
        ids.distinct().size shouldBe ids.size
    }
})
