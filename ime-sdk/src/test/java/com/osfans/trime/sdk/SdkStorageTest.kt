package com.osfans.trime.sdk

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import kotlin.io.path.createTempDirectory

class SdkStorageTest : StringSpec({
    "SDK files stay in a child directory without migrating host files" {
        val root = createTempDirectory("trime-sdk-files").toFile()
        try {
            val hostFiles = listOf("symbol_history", "rime_sync_index.json").map {
                File(root, it).apply { writeText("host") }
            }

            val sdkRoot = SdkStorage.scopedRoot(root, legacy = false)
            sdkRoot shouldBe File(root, "trime-sdk")
            sdkRoot.isDirectory shouldBe true
            hostFiles.forEach { hostFile ->
                File(sdkRoot, hostFile.name).exists() shouldBe false
                File(sdkRoot, hostFile.name).writeText("sdk")
                hostFile.readText() shouldBe "host"
            }
        } finally {
            root.deleteRecursively()
        }
    }

    "standalone legacy storage retains the original root and data" {
        val root = createTempDirectory("trime-legacy-files").toFile()
        try {
            val history = File(root, "symbol_history").apply { writeText("legacy") }

            SdkStorage.scopedRoot(root, legacy = true) shouldBe root
            history.readText() shouldBe "legacy"
            File(root, "trime-sdk").exists() shouldBe false
        } finally {
            root.deleteRecursively()
        }
    }

    "accepts a plain file name" {
        SdkStorage.validateFileName("user.dict") shouldBe "user.dict"
    }

    "rejects path traversal and separators" {
        listOf("", " ", ".", "..", "../user.dict", "/user.dict", "dir/user.dict", "dir\\user.dict", "bad\u0000name")
            .forEach { name ->
                shouldThrow<IllegalArgumentException> { SdkStorage.validateFileName(name) }
            }
    }

    "creates isolated temporary directories and cleans only its own file" {
        val root = createTempDirectory("trime-sdk-cache").toFile()
        try {
            val sibling = File(root, "payload.txt").apply { writeText("host") }
            val sdkRoot = SdkStorage.scopedRoot(root, legacy = false)
            val first = SdkTempFile.create(sdkRoot, "payload.txt")
            val second = SdkTempFile.create(sdkRoot, "payload.txt")

            first.directory shouldNotBe second.directory
            first.directory.parentFile shouldBe sdkRoot
            first.file.name shouldBe "payload.txt"
            first.file.writeText("sdk")
            first.close()

            sibling.readText() shouldBe "host"
            first.file.exists() shouldBe false
            first.directory.exists() shouldBe false
            second.directory.exists() shouldBe true

            second.close()
        } finally {
            root.deleteRecursively()
        }
    }

    "rejects an unsafe temporary file name before creating a directory" {
        val root = createTempDirectory("trime-sdk-cache").toFile()
        try {
            listOf("../payload.txt", "/payload.txt", "dir/payload.txt", "dir\\payload.txt", ".", "..")
                .forEach { name ->
                    shouldThrow<IllegalArgumentException> { SdkTempFile.create(root, name) }
                    root.listFiles().orEmpty().size shouldBe 0
                }
        } finally {
            root.deleteRecursively()
        }
    }

    "closes a temporary file when its operation fails without deleting host or active operation files" {
        val root = createTempDirectory("trime-sdk-cache").toFile()
        try {
            val hostFile = File(root, "payload.txt").apply { writeText("host") }
            SdkTempFile.create(root, "payload.txt").use { active ->
                active.file.writeText("active")
                lateinit var failed: SdkTempFile
                val failure = IllegalStateException("operation failed")

                shouldThrow<IllegalStateException> {
                    SdkTempFile.create(root, "payload.txt").use {
                        failed = it
                        it.file.writeText("partial")
                        throw failure
                    }
                } shouldBe failure

                failed.directory.exists() shouldBe false
                active.file.readText() shouldBe "active"
                hostFile.readText() shouldBe "host"
            }
        } finally {
            root.deleteRecursively()
        }
    }
})
