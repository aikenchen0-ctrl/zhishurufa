package com.osfans.trime.sdk

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory

class LocalDraftStoreTest : StringSpec({
    val first = DraftItem("first", "Reply", listOf(
        DraftSegment("old", DraftChange.REMOVED),
        DraftSegment("new", DraftChange.ADDED),
        DraftSegment(" kept"),
    ))
    val second = DraftItem("second", "Other", listOf(DraftSegment("replacement")))

    "missing storage returns no drafts without creating files" {
        withDraftDirectory { root ->
            LocalDraftStore(root).load() shouldBe emptyList()
            root.listFiles().orEmpty().toList() shouldBe emptyList()
        }
    }

    "schema one round trip preserves changes and only stores draft data" {
        withDraftDirectory { root ->
            val store: DraftStore = LocalDraftStore(root)
            store.save(listOf(first, second))

            LocalDraftStore(root).load() shouldBe listOf(first, second)
            val saved = Json.parseToJsonElement(draftFile(root).readText()).jsonObject
            saved.keys shouldBe setOf("schemaVersion", "items")
            saved.getValue("schemaVersion").jsonPrimitive.content shouldBe "1"
            File(root, "trime-sdk").listFiles().orEmpty().map { it.name } shouldBe listOf("drafts.json")
        }
    }

    "restored drafts do not persist AI request context" {
        withDraftDirectory { root ->
            val generated = first.copy(aiRequest = AiDraftRequest("润色", "私密输入内容"))
            val store = LocalDraftStore(root)
            store.save(listOf(generated))

            LocalDraftStore(root).load().single().aiRequest shouldBe null
            draftFile(root).readText() shouldContain "new"
            draftFile(root).readText() shouldContain "schemaVersion"
            draftFile(root).readText() shouldNotContain "私密输入内容"
        }
    }

    "successive saves atomically replace the previous complete draft list" {
        withDraftDirectory { root ->
            val store = LocalDraftStore(root)
            store.save(listOf(first))
            store.save(listOf(second))
            store.load() shouldBe listOf(second)
            store.save(emptyList())
            store.load() shouldBe emptyList()
        }
    }

    "clear removes only owned draft files and is idempotent" {
        withDraftDirectory { root ->
            val store = LocalDraftStore(root)
            store.save(listOf(first))
            val host = File(root, "host.json").apply { writeText("host") }
            val sibling = File(root, "trime-sdk/settings.json").apply { writeText("settings") }
            store.clear()
            store.clear()

            store.load() shouldBe emptyList()
            draftFile(root).exists() shouldBe false
            host.readText() shouldBe "host"
            sibling.readText() shouldBe "settings"
        }
    }

    "save rejects duplicate ids and more than fifty drafts without modifying saved data" {
        withDraftDirectory { root ->
            val store = LocalDraftStore(root)
            store.save(listOf(first))
            val original = draftFile(root).readText()
            shouldThrow<DraftStoreException> { store.save(listOf(second, second)) }
            shouldThrow<DraftStoreException> { store.save(List(51) { second.copy(id = "id-$it") }) }
            draftFile(root).readText() shouldBe original
            store.load() shouldBe listOf(first)
        }
    }

    "save revalidates mutable segment collections before persistence" {
        withDraftDirectory { root ->
            val segments = mutableListOf(DraftSegment("valid"))
            val invalid = DraftItem("mutable", "", segments)
            segments.add(DraftSegment("x".repeat(16_384)))
            shouldThrow<DraftStoreException> { LocalDraftStore(root).save(listOf(invalid)) }
            draftFile(root).exists() shouldBe false
        }
    }

    "encoded payload above four mebibytes is rejected without replacing previous drafts" {
        withDraftDirectory { root ->
            val store = LocalDraftStore(root)
            store.save(listOf(first))
            val expanded = List(50) { DraftItem("id-$it", "", listOf(DraftSegment("\u0000".repeat(16_384)))) }
            shouldThrow<DraftStoreException> { store.save(expanded) }.message.orEmpty() shouldContain "4194304"
            store.load() shouldBe listOf(first)
        }
    }

    "unknown schema reports its version and preserves the file until explicit clear" {
        withDraftDirectory { root ->
            val unknown = "{\"schemaVersion\":2,\"items\":[]}"
            draftFile(root).apply { checkNotNull(parentFile).mkdirs(); writeText(unknown) }
            val store = LocalDraftStore(root)
            shouldThrow<DraftStoreException> { store.load() }.message.orEmpty() shouldContain "2"
            shouldThrow<DraftStoreException> { store.save(listOf(first)) }
            draftFile(root).readText() shouldBe unknown
            store.clear()
            store.save(listOf(first))
            store.load() shouldBe listOf(first)
        }
    }

    "corrupt and invalid stored data raises a diagnostic error without changing bytes" {
        withDraftDirectory { root ->
            val invalidDocuments = listOf(
                "{broken",
                "{\"items\":[]}",
                "{\"schemaVersion\":1,\"items\":[{\"id\":\"\",\"title\":\"\",\"segments\":[]}]}",
                "{\"schemaVersion\":1,\"items\":[{\"id\":\"a\",\"title\":\"\",\"segments\":[]},{\"id\":\"a\",\"title\":\"\",\"segments\":[]}]}",
            )
            for (invalid in invalidDocuments) {
                draftFile(root).apply { checkNotNull(parentFile).mkdirs(); writeText(invalid) }
                shouldThrow<DraftStoreException> { LocalDraftStore(root).load() }
                draftFile(root).readText() shouldBe invalid
            }
        }
    }

    "load rejects files above the byte limit without deleting them" {
        withDraftDirectory { root ->
            val file = draftFile(root).apply { checkNotNull(parentFile).mkdirs(); writeBytes(ByteArray(4 * 1024 * 1024 + 1)) }
            shouldThrow<DraftStoreException> { LocalDraftStore(root).load() }.message.orEmpty() shouldContain "4194304"
            file.length() shouldBe 4L * 1024 * 1024 + 1
        }
    }

    "an interrupted replacement recovers the last committed data from its backup" {
        withDraftDirectory { root ->
            val store = LocalDraftStore(root)
            store.save(listOf(first))
            draftFile(root).renameTo(File(root, "trime-sdk/drafts.json.bak")) shouldBe true
            draftFile(root).writeText("{partial")

            store.load() shouldBe listOf(first)
            store.save(listOf(second))
            store.load() shouldBe listOf(second)
            File(root, "trime-sdk/drafts.json.bak").exists() shouldBe false
        }
    }

    "an unusable backup path fails without destroying the committed file" {
        withDraftDirectory { root ->
            val store = LocalDraftStore(root)
            store.save(listOf(first))
            val original = draftFile(root).readText()
            File(root, "trime-sdk/drafts.json.bak").mkdir() shouldBe true

            shouldThrow<DraftStoreException> { store.save(listOf(second)) }
            draftFile(root).readText() shouldBe original
        }
    }
})

private fun draftFile(root: File): File = File(root, "trime-sdk/drafts.json")

private suspend fun withDraftDirectory(block: suspend (File) -> Unit) {
    val root = createTempDirectory("trime-drafts-test").toFile()
    try {
        block(root)
    } finally {
        root.deleteRecursively()
    }
}
