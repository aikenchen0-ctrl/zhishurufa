package com.osfans.trime.daemon

import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeMessage

/** 输入法服务使用的内部桥接，不扩展宿主可见的 RimeSession 契约。 */
internal interface RimeSessionBridge {
    suspend fun <T> runOnReadyWithBoundary(jobId: Long, block: suspend RimeApi.() -> T): T

    fun addMessageHandler(handler: (RimeMessage<*>) -> Unit)

    fun removeMessageHandler(handler: (RimeMessage<*>) -> Unit)
}
