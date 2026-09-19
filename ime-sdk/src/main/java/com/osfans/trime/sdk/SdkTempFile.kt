package com.osfans.trime.sdk

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID

/** 每次 SDK 临时文件操作使用独立目录，避免触碰宿主缓存中的同名文件。 */
internal class SdkTempFile private constructor(
    internal val directory: File,
    internal val file: File,
) : Closeable {
    override fun close() {
        file.delete()
        directory.delete()
    }

    companion object {
        fun create(root: File, fileName: String): SdkTempFile {
            val safeName = SdkStorage.validateFileName(fileName)
            val directory = createDirectory(root)
            return SdkTempFile(directory, File(directory, safeName))
        }

        private fun createDirectory(root: File): File {
            if (!root.isDirectory && !root.mkdirs() && !root.isDirectory) {
                throw IOException("Failed to create SDK cache directory")
            }
            repeat(100) {
                val directory = File(root, "operation-${UUID.randomUUID()}")
                if (directory.mkdir()) return directory
            }
            throw IOException("Failed to create temporary SDK directory")
        }
    }
}
