package com.osfans.trime.sdk

/** SDK 对外暴露的常用中文输入方案标识；具体词库和转换仍由 Rime 负责。 */
enum class InputScheme(val id: String) {
    PINYIN("luna_pinyin_simp"),
    T9_PINYIN("pinyin_t9"),
    DOUBLE_PINYIN("double_pinyin"),
    FLYPY("double_pinyin_flypy"),
    MSPY("double_pinyin_mspy"),
    ABC("double_pinyin_abc"),
    SOUGOU("double_pinyin_sogou"),
    ZIGUANG("double_pinyin_ziguang"),
    WUBI86("wubi86"),
    STROKE("stroke");

    companion object {
        private val byId = entries.associateBy(InputScheme::id)

        @JvmStatic
        fun fromId(id: String): InputScheme? = byId[id]
    }
}
