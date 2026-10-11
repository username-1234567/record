package com.suitanglu.app.ai

import com.suitanglu.app.data.AiConfig
import com.suitanglu.app.util.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class ChatPart(val type: String, val text: String? = null, val imageUrl: String? = null)

class DeepSeekClient(
    private val config: AiConfig,
    private val compressImages: Boolean = false,
    private val compressQuality: Int = 80,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun base(): String = config.baseUrl.trimEnd('/')

    private fun chatUrl(): String {
        val b = base()
        return if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds.coerceAtLeast(30).toLong(), TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /** content 数组：文本 + 多张图片（data URI） */
    private fun contentParts(prompt: String, images: List<Pair<ByteArray, String>>) = buildJsonArray {
        add(buildJsonObject { put("type", "text"); put("text", prompt) })
        for ((bytes, mime) in images) {
            add(
                buildJsonObject {
                    put("type", "image_url")
                    put(
                        "image_url",
                        buildJsonObject {
                            put("url", "data:$mime;base64,${android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)}")
                        },
                    )
                },
            )
        }
    }

    fun buildRequestBody(prompt: String, imageFiles: List<File>, stream: Boolean): JsonObject =
        buildJsonObject {
            put("model", config.model.ifBlank { "deepseek-flash" })
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            if (imageFiles.isEmpty()) {
                                put("content", prompt)
                            } else {
                                put("content", contentParts(prompt, imageFiles.map { ImageUtil.loadForUpload(it, compressImages, compressQuality) }))
                            }
                        },
                    )
                },
            )
            put("stream", stream)
            put("max_tokens", config.maxTokens.coerceIn(256, 32768))
        }

    /** 发送标记：stream=true 时通过 onDelta 回传增量文本，返回完整回复 */
    suspend fun chat(prompt: String, imageFiles: List<File>, onDelta: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(
                JsonObject.serializer(),
                buildRequestBody(prompt, imageFiles, config.stream),
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(chatUrl())
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Accept", if (config.stream) "text/event-stream" else "application/json")
                .post(body)
                .build()

            val response = client().newCall(request).await()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val err = runCatching { resp.body?.string() }.getOrNull().orEmpty().take(500)
                    throw IOException("HTTP ${resp.code}: ${err.ifBlank { resp.message }}")
                }
                if (config.stream) {
                    readSse(resp, onDelta)
                } else {
                    val text = resp.body?.string().orEmpty()
                    parseNonStream(text)
                }
            }
        }

    private fun readSse(response: Response, onDelta: (String) -> Unit): String {
        val source = response.body?.source() ?: throw IOException("空响应")
        val sb = StringBuilder()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            val delta = runCatching {
                val obj = json.parseToJsonElement(payload).jsonObject
                obj["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.content
            }.getOrNull() ?: continue
            if (delta.isNotEmpty()) {
                sb.append(delta)
                onDelta(delta)
            }
        }
        return sb.toString()
    }

    private fun parseNonStream(text: String): String = runCatching {
        json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
    }.getOrNull() ?: throw IOException("响应解析失败: ${text.take(300)}")

    /** 测试连接：GET /models */
    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${base()}/models")
            .header("Authorization", "Bearer ${config.apiKey}")
            .get()
            .build()
        val response = client().newCall(request).await()
        response.use { resp ->
            val body = runCatching { resp.body?.string() }.getOrNull().orEmpty()
            if (resp.isSuccessful) {
                val models = runCatching {
                    json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
                        ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                }.getOrNull().orEmpty()
                "连接成功，可用模型：${models.joinToString("、").ifBlank { "(未返回列表)" }}"
            } else {
                "失败 HTTP ${resp.code}: ${body.take(300).ifBlank { resp.message }}"
            }
        }
    }

    suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response)
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }
}
