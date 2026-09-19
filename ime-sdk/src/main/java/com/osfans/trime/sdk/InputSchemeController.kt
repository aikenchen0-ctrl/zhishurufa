package com.osfans.trime.sdk

import android.os.Looper
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun interface InputSchemeRequestHandler {
    fun request(scheme: InputScheme): Boolean
}

/** 只管理方案请求与当前方案状态，不承载 Rime 或宿主业务实现。 */
class InputSchemeController internal constructor() {
    private val mutableCurrent = MutableStateFlow<InputScheme?>(null)
    val current: StateFlow<InputScheme?> = mutableCurrent.asStateFlow()
    @Volatile private var handler: InputSchemeRequestHandler? = null

    @MainThread
    fun request(scheme: InputScheme): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper()) { "请在主线程调用输入方案接口" }
        return handler?.request(scheme) == true
    }

    internal fun publish(id: String?) {
        mutableCurrent.value = id?.let(InputScheme::fromId)
    }

    internal fun attach(value: InputSchemeRequestHandler) {
        handler = value
    }

    internal fun detach(value: InputSchemeRequestHandler) {
        if (handler === value) {
            handler = null
            mutableCurrent.value = null
        }
    }
}
