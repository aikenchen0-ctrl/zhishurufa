package com.osfans.trime.sdk

import android.os.Looper
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** SDK 稳定暴露的常用键盘布局；实际按键仍由当前主题提供。 */
enum class KeyboardLayout(val id: String) {
    DEFAULT("default"),
    QWERTY("qwerty"),
    LETTER("letter"),
    T9_PINYIN("pinyin_t9"),
    NUMBER("number"),
    PHONE("phone"),
    DATETIME("datetime"),
    SYMBOLS("symbols"),
    EDIT("edit"),
    WUBI86("wubi86"),
    STROKE("stroke");

    companion object {
        private val byId = entries.associateBy(KeyboardLayout::id)

        @JvmStatic
        fun fromId(id: String): KeyboardLayout? = byId[id]
    }
}

internal fun interface KeyboardLayoutRequestHandler {
    fun request(layout: KeyboardLayout): Boolean
}

/** 管理宿主对当前输入法键盘布局的显式请求与状态发布。 */
class KeyboardLayoutController internal constructor() {
    private val mutableCurrent = MutableStateFlow<KeyboardLayout?>(null)
    val current: StateFlow<KeyboardLayout?> = mutableCurrent.asStateFlow()
    @Volatile private var handler: KeyboardLayoutRequestHandler? = null

    @MainThread
    fun request(layout: KeyboardLayout): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper()) { "请在主线程调用键盘布局接口" }
        return handler?.request(layout) == true
    }

    internal fun publish(id: String?) {
        mutableCurrent.value = id?.let(KeyboardLayout::fromId)
    }

    internal fun attach(value: KeyboardLayoutRequestHandler) {
        handler = value
    }

    internal fun detach(value: KeyboardLayoutRequestHandler) {
        if (handler === value) {
            handler = null
            mutableCurrent.value = null
        }
    }
}

