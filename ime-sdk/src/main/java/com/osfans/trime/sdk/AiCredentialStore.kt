package com.osfans.trime.sdk

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 使用 Android Keystore 保存内置 AI 配置；低于 API 23 时只支持运行时配置。 */
class AiCredentialStore(context: Context) {
    private val appContext = context.applicationContext
    private val alias = "${appContext.packageName}.trime.ai.settings"
    private val file = File(appContext.noBackupFilesDir, "trime-sdk/ai-settings.bin")

    fun save(settings: AiSettings): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(AiSettingsCodec.encode(settings).toByteArray(Charsets.UTF_8))
            val payload = cipher.iv + encrypted
            val parent = file.parentFile ?: error("AI 配置目录无效")
            check(parent.exists() || parent.mkdirs()) { "AI 配置目录创建失败" }
            val incoming = File(parent, "${file.name}.incoming")
            incoming.writeBytes(payload)
            val backup = File(parent, "${file.name}.bak")
            requireRegularFile(backup)
            if (!backup.exists() && file.exists()) {
                check(file.renameTo(backup)) { "AI 配置备份失败" }
            }
            // 进程中断后可能留下主文件和备份；主文件只会来自完整的 rename，优先保留它。
            if (backup.exists() && file.exists()) {
                check(file.delete()) { "AI 配置替换失败" }
            }
            check(incoming.renameTo(file)) { "AI 配置提交失败，旧配置仍保留" }
            if (backup.exists()) check(backup.delete()) { "AI 配置清理失败" }
            true
        }.getOrDefault(false)
    }

    fun load(): AiSettings = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return@runCatching AiSettings()
        if (file.isFile) decrypt(file) else throw IllegalStateException("AI 配置主文件不存在")
    }.recoverCatching {
        val backup = File(file.parentFile, "${file.name}.bak")
        if (!backup.isFile) throw it
        decrypt(backup)
    }.getOrDefault(AiSettings())

    fun clear(): Boolean = runCatching {
        val parent = file.parentFile
        val backup = parent?.let { File(it, "${file.name}.bak") }
        val incoming = parent?.let { File(it, "${file.name}.incoming") }
        listOfNotNull(file, backup, incoming).forEach { candidate ->
            requireRegularFile(candidate)
            if (candidate.exists() && !candidate.delete()) error("AI 配置清理失败")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(alias)
        }
        true
    }.getOrDefault(false)

    private fun decrypt(source: File): AiSettings {
        require(source.isFile) { "AI 配置不存在" }
        val payload = source.readBytes()
        require(payload.size > IV_LENGTH) { "AI 配置损坏" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, payload.copyOf(IV_LENGTH)))
        return AiSettingsCodec.decode(String(cipher.doFinal(payload.copyOfRange(IV_LENGTH, payload.size)), Charsets.UTF_8))
    }

    private fun requireRegularFile(candidate: File) {
        check(!candidate.exists() || candidate.isFile) { "AI 配置路径不是普通文件" }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
    }
}
