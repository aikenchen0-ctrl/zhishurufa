/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.userdict

import com.osfans.trime.sdk.SdkStorage
import com.osfans.trime.util.appContext
import java.io.InputStream
import java.io.OutputStream

object UserDictManager {
    fun restoreUserDict(stream: InputStream, snapshotFile: String): Result<Unit> {
        return SdkStorage.withTempFile(appContext, snapshotFile) { tempFile ->
            tempFile.outputStream().use { stream.copyTo(it) }
            if (restoreUserDict(tempFile.absolutePath)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to restore"))
            }
        }
    }

    fun importUserDict(stream: InputStream, dictName: String, textFile: String): Result<Int> {
        return SdkStorage.withTempFile(appContext, textFile) { tempFile ->
            tempFile.outputStream().use { stream.copyTo(it) }
            val count = importUserDict(dictName, tempFile.absolutePath)
            if (count >= 0) {
                Result.success(count)
            } else {
                Result.failure(Exception("Failed to import from '$textFile' to '$dictName'"))
            }
        }
    }

    fun exportUserDict(dest: OutputStream, dictName: String, textFile: String): Result<Int> {
        return try {
            SdkStorage.withTempFile(appContext, textFile) { tempFile ->
                val count = exportUserDict(dictName, tempFile.absolutePath)
                if (count >= 0 && tempFile.exists()) {
                    tempFile.inputStream().use { it.copyTo(dest) }
                    Result.success(count)
                } else {
                    Result.failure(Exception("Failed to export '$dictName' to '$textFile'"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @JvmStatic
    external fun getUserDictList(): Array<String>

    @JvmStatic
    external fun backupUserDict(dictName: String): Boolean

    @JvmStatic
    external fun restoreUserDict(snapshotFile: String): Boolean

    @JvmStatic
    external fun exportUserDict(dictName: String, textFile: String): Int

    @JvmStatic
    external fun importUserDict(dictName: String, textFile: String): Int
}
