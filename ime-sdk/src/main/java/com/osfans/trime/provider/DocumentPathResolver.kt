package com.osfans.trime.provider

import java.io.File
import java.io.FileNotFoundException

internal class DocumentPathResolver(directory: File) {
    val root: File = directory.canonicalFile

    fun contains(file: File): Boolean {
        val path = file.canonicalPath
        return path == root.path || path.startsWith(root.path + File.separator)
    }

    fun id(file: File): String {
        val canonical = checked(file)
        return if (canonical == root) "files" else "files/${canonical.relativeTo(root).invariantSeparatorsPath}"
    }

    fun resolve(documentId: String): File {
        if (documentId == "files") return root
        if (!documentId.startsWith("files/")) throw FileNotFoundException("文档不属于输入法目录")
        return checked(File(root, documentId.removePrefix("files/")))
    }

    fun child(parent: File, name: String): File {
        if (name.isBlank() || name == "." || name == ".." || '/' in name || '\\' in name) {
            throw FileNotFoundException("文件名不能包含路径")
        }
        return checked(File(checked(parent), name))
    }

    fun isChild(parentId: String, childId: String): Boolean =
        resolve(childId).path.startsWith(resolve(parentId).path + File.separator)

    fun requireMutable(file: File) {
        if (checked(file) == root) throw FileNotFoundException("不能修改输入法根目录")
    }

    private fun checked(file: File): File = file.canonicalFile.also {
        if (!contains(it)) throw FileNotFoundException("文档路径超出输入法目录")
    }
}
