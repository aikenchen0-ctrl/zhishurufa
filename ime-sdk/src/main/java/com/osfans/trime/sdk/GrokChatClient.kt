package com.osfans.trime.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** OpenAI 兼容 Chat Completions 客户端，可服务 Grok、OpenAI 或兼容代理。 */
class OpenAiCompatibleChatClient(private val configuration: AiConfiguration) : AiDraftStreamingProvider {
    override suspend fun generate(request: AiDraftRequest): String = withContext(Dispatchers.IO) {
        val connection = (URL(GrokChatProtocol.chatCompletionsUrl(configuration.endpoint)).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = configuration.connectTimeoutMillis
            connection.readTimeout = configuration.readTimeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${configuration.apiKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(GrokChatProtocol.encode(configuration.model, request).toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw AiClientException("AI 请求失败：HTTP $status")
            GrokChatProtocol.decode(response)
        } catch (error: AiClientException) {
            throw error
        } catch (error: IOException) {
            throw AiClientException("AI 网络请求失败", error)
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun generateStreaming(
        request: AiDraftRequest,
        onChunk: suspend (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(GrokChatProtocol.chatCompletionsUrl(configuration.endpoint)).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = configuration.connectTimeoutMillis
            connection.readTimeout = configuration.readTimeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${configuration.apiKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "text/event-stream, application/json")
            connection.outputStream.use { it.write(GrokChatProtocol.encode(configuration.model, request, stream = true).toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val reader = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?: throw AiClientException("AI 返回为空")
            if (status !in 200..299) {
                reader.use { it.readText() }
                throw AiClientException("AI 请求失败：HTTP $status")
            }
            val result = StringBuilder()
            val raw = StringBuilder()
            reader.use {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = it.readLine() ?: break
                    currentCoroutineContext().ensureActive()
                    raw.appendLine(line)
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    val delta = GrokChatProtocol.decodeDelta(payload)
                    if (delta.isNotEmpty()) {
                        result.append(delta)
                        onChunk(result.toString())
                    }
                }
            }
            result.toString().trim().takeIf { it.isNotEmpty() }
                ?: GrokChatProtocol.decode(raw.toString())
        } catch (error: AiClientException) {
            throw error
        } catch (error: IOException) {
            throw AiClientException("AI 网络请求失败", error)
        } finally {
            connection.disconnect()
        }
    }
}

typealias GrokChatClient = OpenAiCompatibleChatClient

internal object GrokChatProtocol {
    private val json = Json { ignoreUnknownKeys = true }

    fun chatCompletionsUrl(endpoint: String): String {
        val parsed = runCatching { URI(endpoint.trim()) }.getOrElse { throw IllegalArgumentException("AI 端点无效") }
        require(parsed.scheme == "https") { "AI 端点必须使用 HTTPS" }
        require(parsed.host != null) { "AI 端点缺少主机名" }
        val path = parsed.path.trimEnd('/')
        return when {
            path.endsWith("/v1/chat/completions") -> parsed.toString()
            path.endsWith("/v1") -> "${parsed.toString().trimEnd('/')}/chat/completions"
            path.isEmpty() -> "${parsed.toString().trimEnd('/')}/v1/chat/completions"
            else -> "${parsed.toString().trimEnd('/')}/v1/chat/completions"
        }
    }

    fun encode(model: String, request: AiDraftRequest, stream: Boolean = false): String = buildJsonObject {
        put("model", model)
        put("stream", stream)
        put("max_tokens", request.maxOutputTokens)
        put("temperature", 0.7)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", "你是中文输入法的草稿助手。只输出可直接编辑的中文文本，不要解释过程，不要自动发送消息。")
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", "指令：${request.instruction}\n当前上下文：${request.context}")
            })
        })
    }.toString()

    fun decodeDelta(body: String): String = runCatching {
        json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject?.get("content")
            ?.jsonPrimitive?.content.orEmpty()
    }.getOrDefault("")

    fun decode(body: String): String {
        val content = runCatching {
            json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
        }.getOrNull()?.trim().orEmpty()
        if (content.isEmpty()) throw AiClientException("AI 返回内容为空")
        return content
    }
}
