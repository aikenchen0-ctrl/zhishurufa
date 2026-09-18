package com.osfans.trime.provider

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files

class DocumentPathResolverTest : StringSpec({
    val root = Files.createTempDirectory("trime-provider").toFile()
    afterSpec { root.deleteRecursively() }

    "document paths round trip within the SDK directory" {
        val paths = DocumentPathResolver(root)
        paths.resolve("files") shouldBe root.canonicalFile
        paths.resolve("files/rime/user.yaml") shouldBe File(root, "rime/user.yaml").canonicalFile
        paths.id(File(root, "shared")) shouldBe "files/shared"
    }

    "document IDs cannot escape into host files" {
        val paths = DocumentPathResolver(root)
        listOf("files/../host.txt", "files/../../host.txt", "other/data", "/host.txt", "files2/data").forEach {
            shouldThrow<FileNotFoundException> { paths.resolve(it) }
        }
    }

    "new file names cannot introduce parent or absolute paths" {
        val paths = DocumentPathResolver(root)
        listOf("", ".", "..", "../host.txt", "child/host.txt", "child\\host.txt").forEach {
            shouldThrow<FileNotFoundException> { paths.child(root, it) }
        }
        paths.child(root, "draft.yaml") shouldBe File(root, "draft.yaml").canonicalFile
    }

    "a path with a shared prefix is not a child" {
        val paths = DocumentPathResolver(root)
        paths.isChild("files/rime", "files/rime/user.yaml") shouldBe true
        paths.isChild("files/rime", "files/rime-other/user.yaml") shouldBe false
        paths.isChild("files", "files") shouldBe false
    }
})
