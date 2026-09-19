package com.osfans.trime.sdk

/** 仅处理用户明确触发的宿主动作；引擎与布局不依赖具体业务实现。 */
fun interface ImeHostActionHandler {
    fun handle(action: String, payload: String): Boolean
}

internal class HostActionRouter {
    @Volatile
    var handler: ImeHostActionHandler? = null

    fun dispatch(action: String, payload: String): Boolean {
        if (action.isBlank()) return false
        return try {
            handler?.handle(action, payload) ?: false
        } catch (_: Exception) {
            // 宿主功能故障不能中断基础打字，也不记录可能包含草稿的参数。
            false
        }
    }
}
