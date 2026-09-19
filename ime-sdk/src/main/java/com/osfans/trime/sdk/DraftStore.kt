package com.osfans.trime.sdk

import java.io.IOException

/** 由宿主显式调用的草稿存储；只保存草稿展示数据，不保存编辑器会话。 */
interface DraftStore {
    suspend fun save(items: List<DraftItem>)
    suspend fun load(): List<DraftItem>
    suspend fun clear()
}

/** 保留失败原因，便于宿主提示、诊断或选择显式清空。 */
class DraftStoreException(message: String, cause: Throwable? = null) : IOException(message, cause)
