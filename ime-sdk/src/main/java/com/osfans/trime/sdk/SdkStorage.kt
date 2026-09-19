package com.osfans.trime.sdk

import android.content.Context
import com.osfans.trime.R
import java.io.File

internal object SdkStorage {
    private const val SDK_DIRECTORY = "trime-sdk"

    fun usesLegacyPaths(context: Context): Boolean = context.resources.getBoolean(R.bool.trime_legacy_storage)

    fun externalRoot(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return scopedRoot(base, usesLegacyPaths(context))
    }

    fun internalFilesRoot(context: Context): File = scopedRoot(context.filesDir, usesLegacyPaths(context))

    fun cacheRoot(context: Context): File = scopedRoot(context.cacheDir, legacy = false)

    fun databaseName(context: Context, name: String): String = if (usesLegacyPaths(context)) name else "trime_sdk.$name"

    fun validateFileName(name: String): String {
        require(name.isNotBlank() && name != "." && name != "..") { "Invalid file name" }
        require(name.none { it == '/' || it == '\\' || it == '\u0000' }) { "Invalid file name" }
        return name
    }

    inline fun <T> withTempFile(context: Context, fileName: String, block: (File) -> T): T =
        SdkTempFile.create(cacheRoot(context), fileName).use { block(it.file) }

    internal fun scopedRoot(base: File, legacy: Boolean): File =
        (if (legacy) base else File(base, SDK_DIRECTORY)).also { it.mkdirs() }
}
