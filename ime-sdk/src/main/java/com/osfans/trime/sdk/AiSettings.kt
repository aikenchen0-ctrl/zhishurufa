package com.osfans.trime.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class AiSettings(
    val route: AiProviderRoute = AiProviderRoute.DISABLED,
    val configuration: AiConfiguration? = null,
)

/** 仅用于加密载荷；调用方不得把返回值写入普通日志或偏好。 */
internal object AiSettingsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(settings: AiSettings): String = buildJsonObject {
        put("route", settings.route.name)
        settings.configuration?.let { configuration ->
            put("backend", configuration.backend.name)
            put("endpoint", configuration.endpoint)
            put("apiKey", configuration.apiKey)
            put("model", configuration.model)
        }
    }.toString()

    fun decode(payload: String): AiSettings {
        if (payload.isBlank()) return AiSettings()
        return runCatching {
            val root = json.parseToJsonElement(payload).jsonObject
            val route = root.enumOrNull<AiProviderRoute>("route") ?: AiProviderRoute.DISABLED
            val key = root["apiKey"]?.jsonPrimitive?.contentOrNull
            val configuration = if (key.isNullOrBlank()) null else {
                val backend = root.enumOrNull<AiBackend>("backend") ?: AiBackend.GROK
                AiConfiguration(
                    backend = backend,
                    endpoint = root["endpoint"]?.jsonPrimitive?.contentOrNull ?: "https://api.cc2.cx",
                    apiKey = key,
                    model = root["model"]?.jsonPrimitive?.contentOrNull ?: "grok-4.6",
                )
            }
            AiSettings(route, configuration)
        }.getOrDefault(AiSettings())
    }

    private inline fun <reified T : Enum<T>> JsonObject.enumOrNull(name: String): T? =
        this[name]?.jsonPrimitive?.contentOrNull?.let { raw -> runCatching { enumValueOf<T>(raw) }.getOrNull() }
}
