package com.osfans.trime.sdk

import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.coroutineContext
import java.net.URI

/** AI 草稿请求；上下文由调用方明确提供，不自动读取聊天记录。 */
@kotlinx.serialization.Serializable
data class AiDraftRequest(
    val instruction: String,
    val context: String,
    val maxOutputTokens: Int = 256,
) {
    init {
        require(instruction.length <= 8_000) { "AI 指令过长" }
        require(context.length <= 16_384) { "AI 上下文过长" }
        require(maxOutputTokens in 32..1_024) { "AI 输出长度无效" }
    }
}

enum class AiProviderRoute { DISABLED, BUILT_IN, HOST }

enum class AiBackend { GROK, OPENAI }

enum class AiDraftOperation(val instruction: String) {
    REPLY("根据当前内容生成自然、简洁的回复"),
    REWRITE("重写当前内容，保持原意并改善结构"),
    POLISH("润色当前内容，改善表达和语气"),
    CONTINUE("续写当前内容，不改动已有文字"),
    SUGGEST("根据当前内容生成最多三条简短建议，每行一条"),

    ;

    companion object {
        /** 根据当前快照选择输入法内部可用的紧凑操作集合。 */
        fun contextActions(snapshot: EditorSnapshot): List<AiDraftOperation> = when {
            snapshot.hasSelection -> listOf(REWRITE, POLISH)
            snapshot.text.isBlank() -> listOf(REPLY, CONTINUE)
            else -> listOf(REWRITE, POLISH, CONTINUE)
        }
    }
}

fun interface AiDraftProvider {
    suspend fun generate(request: AiDraftRequest): String
}

interface AiDraftStreamingProvider : AiDraftProvider {
    suspend fun generateStreaming(request: AiDraftRequest, onChunk: suspend (String) -> Unit): String

    override suspend fun generate(request: AiDraftRequest): String = generateStreaming(request) {}
}

data class AiConfiguration(
    val backend: AiBackend = AiBackend.GROK,
    val endpoint: String = "https://api.cc2.cx",
    val apiKey: String,
    val model: String = "grok-4.6",
    val connectTimeoutMillis: Int = 15_000,
    val readTimeoutMillis: Int = 60_000,
) {
    init {
        require(apiKey.isNotBlank()) { "AI API 密钥不能为空" }
        require(apiKey.length <= 4_096) { "AI API 密钥过长" }
        val parsedEndpoint = runCatching { URI(endpoint.trim()) }
            .getOrElse { throw IllegalArgumentException("AI 端点无效") }
        require(
            parsedEndpoint.scheme == "https" &&
                !parsedEndpoint.host.isNullOrBlank() &&
                parsedEndpoint.userInfo == null &&
                parsedEndpoint.query == null &&
                parsedEndpoint.fragment == null,
        ) {
            "AI 端点必须使用 HTTPS 且包含主机名"
        }
        require(endpoint.length <= 2_048) { "AI 端点过长" }
        require(model.isNotBlank() && model.length <= 128) { "AI 模型无效" }
        require(connectTimeoutMillis in 1_000..120_000) { "AI 连接超时无效" }
        require(readTimeoutMillis in 1_000..300_000) { "AI 读取超时无效" }
    }

    override fun toString(): String =
        "AiConfiguration(backend=$backend, endpoint=$endpoint, model=$model)"

    companion object {
        fun grok(apiKey: String, endpoint: String = "https://api.cc2.cx", model: String = "grok-4.6") =
            AiConfiguration(AiBackend.GROK, endpoint, apiKey, model)

        fun openAi(apiKey: String, endpoint: String = "https://api.openai.com", model: String = "gpt-4o-mini") =
            AiConfiguration(AiBackend.OPENAI, endpoint, apiKey, model)
    }
}

class AiClientException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** AI 控制器只负责路由和草稿封装，不负责发送消息。 */
class AiController {
    private val mutableRoute = kotlinx.coroutines.flow.MutableStateFlow(AiProviderRoute.DISABLED)
    val route: kotlinx.coroutines.flow.StateFlow<AiProviderRoute> = mutableRoute.asStateFlow()
    private var builtInProvider: AiDraftProvider? = null
    private var hostProvider: AiDraftProvider? = null
    private var draftController: DraftController? = null
    private val generationScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    private val generationLock = Any()
    private val generationJobs = mutableMapOf<String, Job>()
    private val generationTokens = mutableMapOf<String, Long>()

    internal fun attachDraftController(controller: DraftController) {
        draftController = controller
        controller.attachActionHandler(DraftActionHandler { draft ->
            val request = draft.aiRequest ?: return@DraftActionHandler
            generationScope.launch {
                val replacement = generateDraftStreaming(
                    request,
                    draft.id,
                    draft.title,
                    onChunk = {},
                    requestKey = "draft:${draft.id}",
                ) ?: return@launch
                controller.update(draft, replacement)
            }
        })
    }

    /** 取消指定生成槽；同一槽的新请求会自动取消旧请求。 */
    fun cancelGeneration(requestKey: String) {
        synchronized(generationLock) {
            generationTokens[requestKey] = (generationTokens[requestKey] ?: 0L) + 1L
            generationJobs.remove(requestKey)?.cancel()
        }
    }

    fun cancelAllGenerations() {
        synchronized(generationLock) {
            generationTokens.keys.toList().forEach { key -> generationTokens[key] = (generationTokens[key] ?: 0L) + 1L }
            generationJobs.values.toList().forEach(Job::cancel)
            generationJobs.clear()
        }
    }

    private fun beginGeneration(requestKey: String, job: Job?): Long = synchronized(generationLock) {
        generationJobs[requestKey]?.cancel()
        val token = (generationTokens[requestKey] ?: 0L) + 1L
        generationTokens[requestKey] = token
        if (job != null) generationJobs[requestKey] = job
        token
    }

    private fun isCurrent(requestKey: String, token: Long): Boolean = synchronized(generationLock) {
        generationTokens[requestKey] == token
    }

    private fun finishGeneration(requestKey: String, token: Long) {
        synchronized(generationLock) {
            if (generationTokens[requestKey] == token) generationJobs.remove(requestKey)
        }
    }

    fun configureFromSettings(settings: AiSettings) {
        configureBuiltIn(settings.configuration)
        setRoute(settings.route)
    }

    @Synchronized
    fun setBuiltInProvider(provider: AiDraftProvider?) {
        builtInProvider = provider
    }

    @Synchronized
    fun setHostProvider(provider: AiDraftProvider?) {
        hostProvider = provider
    }

    @Synchronized
    fun configureBuiltIn(configuration: AiConfiguration?) {
        builtInProvider = configuration?.let(::GrokChatClient)
    }

    fun setRoute(value: AiProviderRoute) {
        mutableRoute.value = value
    }

    suspend fun generate(request: AiDraftRequest): String? {
        val provider = synchronized(this) {
            when (mutableRoute.value) {
                AiProviderRoute.DISABLED -> null
                AiProviderRoute.BUILT_IN -> builtInProvider
                AiProviderRoute.HOST -> hostProvider
            }
        } ?: return null
        return provider.generate(request).trim().takeIf { it.isNotEmpty() }
    }

    suspend fun generateDraft(
        request: AiDraftRequest,
        id: String,
        title: String,
    ): DraftItem? = generate(request)?.let { DraftItem(id, title, listOf(DraftSegment(it)), request) }

    suspend fun generateDraftStreaming(
        request: AiDraftRequest,
        id: String,
        title: String,
        onChunk: suspend (String) -> Unit,
        requestKey: String = id,
    ): DraftItem? {
        val token = beginGeneration(requestKey, coroutineContext[Job])
        val provider = synchronized(this) {
            when (mutableRoute.value) {
                AiProviderRoute.DISABLED -> null
                AiProviderRoute.BUILT_IN -> builtInProvider
                AiProviderRoute.HOST -> hostProvider
            }
        } ?: run {
            finishGeneration(requestKey, token)
            return null
        }
        return try {
            val safeChunk: suspend (String) -> Unit = { chunk ->
                if (isCurrent(requestKey, token)) onChunk(chunk)
            }
            val text = if (provider is AiDraftStreamingProvider) {
                provider.generateStreaming(request, safeChunk)
            } else {
                provider.generate(request).also { safeChunk(it) }
            }
            if (!isCurrent(requestKey, token)) null
            else text.trim().takeIf { it.isNotEmpty() }?.let { DraftItem(id, title, listOf(DraftSegment(it)), request) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            finishGeneration(requestKey, token)
        }
    }

    /** 生成短建议并写入目标控制器；不触碰编辑器，也不发送消息。 */
    suspend fun generateAndPublishSuggestions(
        request: AiDraftRequest,
        target: SuggestionController,
        idPrefix: String = "suggestion",
    ): List<SuggestionItem>? {
        require(idPrefix.isNotBlank() && idPrefix.length <= 64) { "推荐标识前缀无效" }
        val raw = generate(request) ?: return null
        val items = raw.lineSequence()
            .map(::normalizeSuggestion)
            .filter { it.isNotBlank() && it.length <= 256 }
            .distinct()
            .take(SuggestionController.MAX_ITEMS)
            .mapIndexed { index, text -> SuggestionItem("$idPrefix-$index", text, "AI") }
            .toList()
            .takeIf { it.isNotEmpty() }
            ?: return null
        target.replace(items)
        return items
    }

    private fun normalizeSuggestion(line: String): String = line.trim()
        .replaceFirst(Regex("^(?:[-*]|\\d+[.)、])\\s*"), "")
        .trim()
        .trim('`', '"', '\'', '“', '”', '‘', '’')
}
