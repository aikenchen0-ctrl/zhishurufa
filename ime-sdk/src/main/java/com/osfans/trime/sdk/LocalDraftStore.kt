package com.osfans.trime.sdk

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** 仅在宿主显式调用时读写；文件位于不参与系统备份的应用私有目录。 */
class LocalDraftStore private constructor(private val noBackupRoot: () -> File) : DraftStore {
    constructor(context: Context) : this(noBackupRoot = rootProvider(context))

    internal constructor(noBackupRoot: File) : this({ noBackupRoot })

    override suspend fun save(items: List<DraftItem>) = withStorage("保存") { directory ->
        val snapshot = validatedCopy(items)
        val payload = json.encodeToString(StoredDrafts(SCHEMA_VERSION, snapshot)).toByteArray(Charsets.UTF_8)
        checkSize(payload.size.toLong())
        // 先验证旧数据，防止旧客户端覆盖未知版本或损坏文件。
        readCommitted(directory)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw DraftStoreException("无法创建草稿存储目录")
        }
        val target = File(directory, FILE_NAME)
        val backup = File(directory, BACKUP_NAME)
        val incoming = File(directory, INCOMING_NAME)
        requireRegularFile(incoming)
        try {
            FileOutputStream(incoming).use { output ->
                output.write(payload)
                output.fd.sync()
            }
            if (!backup.exists() && target.exists() && !target.renameTo(backup)) {
                throw DraftStoreException("无法保留上一份草稿文件")
            }
            // 备份存在时主文件尚未提交；删除它不会丢失上一份有效数据。
            if (backup.exists() && target.exists() && !target.delete()) {
                throw DraftStoreException("无法替换未提交的草稿文件")
            }
            if (!incoming.renameTo(target)) {
                throw DraftStoreException("无法原子替换草稿文件，上一份数据仍保留")
            }
            if (backup.exists() && !backup.delete()) {
                throw DraftStoreException("无法完成草稿提交，上一份数据仍保留")
            }
        } finally {
            if (incoming.isFile) incoming.delete()
        }
    }

    override suspend fun load(): List<DraftItem> = withStorage("读取", ::readCommitted)

    override suspend fun clear() = withStorage("清空") { directory ->
        val files = listOf(INCOMING_NAME, FILE_NAME, BACKUP_NAME).map { File(directory, it) }
        files.forEach(::requireRegularFile)
        files.forEach { file ->
            if (file.exists() && !file.delete()) throw DraftStoreException("无法清空草稿文件：${file.name}")
        }
    }

    private suspend fun <T> withStorage(action: String, operation: (File) -> T): T = withContext(Dispatchers.IO) {
        // 同进程的多个存储实例共享锁，避免交错提交或清空。
        storageLock.withLock {
            try {
                val directory = File(noBackupRoot(), "trime-sdk")
                if (directory.exists() && !directory.isDirectory) {
                    throw DraftStoreException("草稿存储路径不是目录")
                }
                operation(directory)
            } catch (error: DraftStoreException) {
                throw error
            } catch (error: IOException) {
                throw DraftStoreException("草稿${action}失败：${error.message}", error)
            } catch (error: IllegalArgumentException) {
                throw DraftStoreException("草稿${action}失败：数据格式或内容无效", error)
            }
        }
    }

    private fun readCommitted(directory: File): List<DraftItem> {
        val backup = File(directory, BACKUP_NAME)
        val target = File(directory, FILE_NAME)
        requireRegularFile(backup)
        requireRegularFile(target)
        // 删除备份才代表提交完成；进程中断后优先读取旧的完整版本。
        val source = if (backup.exists()) backup else target
        if (!source.exists()) return emptyList()
        checkSize(source.length())
        val bytes = ByteArrayOutputStream().use { output ->
            source.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    checkSize(output.size().toLong() + count)
                    output.write(buffer, 0, count)
                }
            }
            output.toByteArray()
        }
        val document = json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)) as? JsonObject
            ?: throw DraftStoreException("草稿文件必须是 JSON 对象")
        val version = document["schemaVersion"] as? JsonPrimitive
        if (version == null || version.isString || version.intOrNull == null) {
            throw DraftStoreException("草稿文件缺少有效的 schemaVersion")
        }
        if (version.intOrNull != SCHEMA_VERSION) {
            throw DraftStoreException("不支持的草稿版本：${version.content}")
        }
        return validatedCopy(json.decodeFromJsonElement<StoredDrafts>(document).items)
    }

    private fun validatedCopy(items: List<DraftItem>): List<DraftItem> {
        if (items.size > MAX_ITEMS) throw DraftStoreException("草稿数量不能超过 $MAX_ITEMS")
        if (items.map { it.id }.distinct().size != items.size) throw DraftStoreException("草稿标识不能重复")
        return items.map { it.copy(segments = it.segments.toList()) }
    }

    private fun requireRegularFile(file: File) {
        if (file.exists() && !file.isFile) throw DraftStoreException("草稿路径不是普通文件：${file.name}")
    }

    private fun checkSize(size: Long) {
        if (size > MAX_BYTES) throw DraftStoreException("草稿文件不能超过 $MAX_BYTES 字节")
    }

    @Serializable
    private data class StoredDrafts(val schemaVersion: Int, val items: List<DraftItem>)

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_ITEMS = 50
        const val MAX_BYTES = 4 * 1024 * 1024
        const val FILE_NAME = "drafts.json"
        const val BACKUP_NAME = "drafts.json.bak"
        const val INCOMING_NAME = "drafts.json.new"
        val json = Json
        val storageLock = Mutex()

        fun rootProvider(context: Context): () -> File {
            val application = context.applicationContext
            return { application.noBackupFilesDir }
        }
    }
}
