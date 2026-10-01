package com.aycho.app.vlm

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * 统一模型网关（ModelGateway）
 *
 * 内部只实现两套协议族：
 *  1) OpenAI 兼容协议（/chat/completions）
 *  2) 原生协议（Gemini generateContent / Claude messages）
 */
class ModelGateway(
    private val apiKey: String,
    baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-4o",
    private val protocol: String = PROTOCOL_OPENAI,
    private val customHeaders: Map<String, String> = emptyMap()
) {
    // 规范化 URL：自动添加 https:// 前缀，移除末尾斜杠
    private val baseUrl: String = normalizeUrl(baseUrl)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))
        .build()

    companion object {
        const val PROTOCOL_OPENAI = "openai"
        const val PROTOCOL_GEMINI = "gemini"
        const val PROTOCOL_CLAUDE = "claude"

        private val JSON_MEDIA = "application/json".toMediaType()
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L

        /** 规范化 URL：自动添加 https:// 前缀，移除末尾斜杠 */
        private fun normalizeUrl(url: String): String {
            var normalized = url.trim().removeSuffix("/")
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "https://$normalized"
            }
            return normalized
        }

        /**
         * 从 API 获取可用模型列表
         * @param baseUrl API 基础地址
         * @param apiKey API 密钥
         * @return 模型 ID 列表
         */
        suspend fun fetchModels(baseUrl: String, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
            // 验证 baseUrl 是否为空
            if (baseUrl.isBlank()) {
                return@withContext Result.failure(Exception("Base URL 不能为空"))
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            // 清理 URL，确保正确拼接
            val cleanBaseUrl = normalizeUrl(baseUrl.removeSuffix("/chat/completions"))

            val request = try {
                Request.Builder()
                    .url("$cleanBaseUrl/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
            } catch (e: IllegalArgumentException) {
                return@withContext Result.failure(Exception("Base URL 格式无效: ${e.message}"))
            }

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val json = JSONObject(responseBody)
                        val data = json.optJSONArray("data") ?: JSONArray()
                        val models = mutableListOf<String>()
                        for (i in 0 until data.length()) {
                            val item = data.optJSONObject(i)
                            if (item != null) {
                                val id = item.optString("id", "").trim()
                                if (id.isNotEmpty()) {
                                    models.add(id)
                                }
                            }
                        }
                        Result.success(models)
                    } else {
                        Result.failure(Exception("HTTP ${response.code}: $responseBody"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 调用 VLM 进行多模态推理 (带重试)
     */
    suspend fun predict(
        prompt: String,
        images: List<Bitmap> = emptyList()
    ): Result<String> {
        // 统一转成 OpenAI 兼容 messages，再由协议层转换
        val encodedImages = images.map { bitmapToBase64Url(it) }
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                    encodedImages.forEach { imageUrl ->
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply { put("url", imageUrl) })
                        })
                    }
                })
            })
        }
        return executeWithRetry(messages)
    }

    /**
     * 统一执行入口：按协议构造请求并解析响应，带指数退避重试
     */
    private suspend fun executeWithRetry(messages: JSONArray): Result<String> =
        withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            for (attempt in 1..MAX_RETRIES) {
                try {
                    val request = buildRequest(messages)
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val text = parseResponse(responseBody)
                        if (!text.isNullOrBlank()) {
                            return@withContext Result.success(text)
                        }
                        lastException = Exception("No response from model")
                    } else {
                        lastException = Exception("API error: ${response.code} - $responseBody")
                    }
                } catch (e: UnknownHostException) {
                    println("[ModelGateway] DNS 解析失败，重试 $attempt/$MAX_RETRIES...")
                    lastException = e
                    if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
                } catch (e: java.net.SocketTimeoutException) {
                    println("[ModelGateway] 请求超时，重试 $attempt/$MAX_RETRIES...")
                    lastException = e
                    if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
                } catch (e: java.io.IOException) {
                    println("[ModelGateway] IO 错误: ${e.message}，重试 $attempt/$MAX_RETRIES...")
                    lastException = e
                    if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
                } catch (e: Exception) {
                    return@withContext Result.failure(e)
                }
            }
            Result.failure(lastException ?: Exception("Unknown error"))
        }

    /** 按协议族构造 HTTP 请求 */
    private fun buildRequest(messages: JSONArray): Request {
        fun Request.Builder.withCustomHeaders(): Request.Builder {
            customHeaders.forEach { (k, v) -> addHeader(k, v) }
            return this
        }
        return when (protocol) {
            PROTOCOL_GEMINI -> {
                val (system, contents) = toGeminiPayload(messages)
                val body = JSONObject().apply {
                    put("contents", contents)
                    if (system != null) put("systemInstruction", system)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.0)
                        put("topP", 0.85)
                        put("maxOutputTokens", 4096)
                    })
                }
                Request.Builder()
                    .url("$baseUrl/models/$model:generateContent")
                    .addHeader("x-goog-api-key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .withCustomHeaders()
                    .post(body.toString().toRequestBody(JSON_MEDIA))
                    .build()
            }

            PROTOCOL_CLAUDE -> {
                val (system, claudeMessages) = toClaudePayload(messages)
                val body = JSONObject().apply {
                    put("model", model)
                    put("max_tokens", 4096)
                    put("temperature", 0.0)
                    if (system.isNotEmpty()) put("system", system)
                    put("messages", claudeMessages)
                }
                Request.Builder()
                    .url("$baseUrl/messages")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("Content-Type", "application/json")
                    .withCustomHeaders()
                    .post(body.toString().toRequestBody(JSON_MEDIA))
                    .build()
            }

            else -> {
                val body = JSONObject().apply {
                    put("model", model)
                    put("messages", messages)
                    put("max_tokens", 4096)
                    put("temperature", 0.0)
                    put("top_p", 0.85)
                    put("frequency_penalty", 0.2)
                }
                Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .withCustomHeaders()
                    .post(body.toString().toRequestBody(JSON_MEDIA))
                    .build()
            }
        }
    }

    /** 按协议族解析响应文本 */
    private fun parseResponse(body: String): String? = try {
        when (protocol) {
            PROTOCOL_GEMINI -> {
                val candidates = JSONObject(body).optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    null
                } else {
                    val parts = candidates.getJSONObject(0)
                        .optJSONObject("content")?.optJSONArray("parts")
                    if (parts == null) null
                    else {
                        val sb = StringBuilder()
                        for (i in 0 until parts.length()) {
                            sb.append(parts.getJSONObject(i).optString("text", ""))
                        }
                        sb.toString().ifBlank { null }
                    }
                }
            }

            PROTOCOL_CLAUDE -> {
                val arr = JSONObject(body).optJSONArray("content")
                if (arr == null) null
                else {
                    val sb = StringBuilder()
                    for (i in 0 until arr.length()) {
                        sb.append(arr.getJSONObject(i).optString("text", ""))
                    }
                    sb.toString().ifBlank { null }
                }
            }

            else -> {
                val choices = JSONObject(body).optJSONArray("choices")
                if (choices == null || choices.length() == 0) null
                else choices.getJSONObject(0).optJSONObject("message")
                    ?.optString("content", "")?.ifBlank { null }
            }
        }
    } catch (e: Exception) {
        null
    }

    /** OpenAI messages -> Gemini contents / systemInstruction */
    private fun toGeminiPayload(messages: JSONArray): Pair<JSONObject?, JSONArray> {
        val contents = JSONArray()
        var system: JSONObject? = null
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val role = m.optString("role", "user")
            if (role == "system") {
                val text = m.optString("content", "")
                if (text.isNotEmpty()) {
                    system = JSONObject().put("parts", JSONArray().apply {
                        put(JSONObject().put("text", text))
                    })
                }
                continue
            }
            val parts = JSONArray()
            when (val content = m.opt("content")) {
                is String -> parts.put(JSONObject().put("text", content))
                is JSONArray -> for (j in 0 until content.length()) {
                    val part = content.getJSONObject(j)
                    when (part.optString("type")) {
                        "text" -> parts.put(JSONObject().put("text", part.optString("text", "")))
                        "image_url" -> {
                            val url = part.optJSONObject("image_url")?.optString("url", "") ?: ""
                            if (url.startsWith("data:")) {
                                val (mime, raw) = dataUrlToRaw(url)
                                parts.put(JSONObject().put("inline_data", JSONObject().apply {
                                    put("mime_type", mime)
                                    put("data", raw)
                                }))
                            }
                        }
                    }
                }
            }
            contents.put(JSONObject().apply {
                put("role", if (role == "assistant") "model" else "user")
                put("parts", parts)
            })
        }
        return system to contents
    }

    /** OpenAI messages -> Claude messages / system */
    private fun toClaudePayload(messages: JSONArray): Pair<String, JSONArray> {
        val out = JSONArray()
        val systemBuilder = StringBuilder()
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val role = m.optString("role", "user")
            if (role == "system") {
                systemBuilder.append(m.optString("content", ""))
                continue
            }
            val blocks = JSONArray()
            when (val content = m.opt("content")) {
                is String -> blocks.put(JSONObject().put("type", "text").put("text", content))
                is JSONArray -> for (j in 0 until content.length()) {
                    val part = content.getJSONObject(j)
                    when (part.optString("type")) {
                        "text" -> blocks.put(JSONObject().put("type", "text").put("text", part.optString("text", "")))
                        "image_url" -> {
                            val url = part.optJSONObject("image_url")?.optString("url", "") ?: ""
                            if (url.startsWith("data:")) {
                                val (mime, raw) = dataUrlToRaw(url)
                                blocks.put(JSONObject().apply {
                                    put("type", "image")
                                    put("source", JSONObject().apply {
                                        put("type", "base64")
                                        put("media_type", mime)
                                        put("data", raw)
                                    })
                                })
                            }
                        }
                    }
                }
            }
            out.put(JSONObject().put("role", role).put("content", blocks))
        }
        return systemBuilder.toString() to out
    }

    /** "data:image/jpeg;base64,xxx" -> ("image/jpeg", "xxx") */
    private fun dataUrlToRaw(dataUrl: String): Pair<String, String> {
        val mime = dataUrl.substringAfter("data:", "image/jpeg").substringBefore(";")
        val raw = dataUrl.substringAfter("base64,", "")
        return mime to raw
    }

    /**
     * 调用 VLM 进行多模态推理 (使用完整对话历史)
     * @param messagesJson OpenAI 兼容的 messages JSON 数组
     */
    suspend fun predictWithContext(
        messagesJson: JSONArray
    ): Result<String> = executeWithRetry(messagesJson)

    /**
     * Bitmap 转 Base64 URL (只压缩质量，不压缩分辨率)
     * 保持原始分辨率以确保坐标准确
     */
    private fun bitmapToBase64Url(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // 使用 JPEG 格式，质量 70%，保持原始分辨率
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val bytes = outputStream.toByteArray()
        println("[ModelGateway] 图片压缩: ${bitmap.width}x${bitmap.height}, ${bytes.size / 1024}KB")
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    /**
     * 调整图片大小
     */
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}

/**
 * 常用 VLM 配置
 */
object ModelGatewayConfigs {
    // OpenAI 兼容协议端点
    fun openAiCompatible(apiKey: String, baseUrl: String, model: String) = ModelGateway(
        apiKey = apiKey,
        baseUrl = baseUrl,
        model = model,
        protocol = ModelGateway.PROTOCOL_OPENAI
    )

    // Gemini 原生协议
    fun gemini(apiKey: String, model: String = "gemini-2.5-flash") = ModelGateway(
        apiKey = apiKey,
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        model = model,
        protocol = ModelGateway.PROTOCOL_GEMINI
    )

    // Claude 原生协议
    fun claude(apiKey: String, model: String = "claude-sonnet-4.5") = ModelGateway(
        apiKey = apiKey,
        baseUrl = "https://api.anthropic.com/v1",
        model = model,
        protocol = ModelGateway.PROTOCOL_CLAUDE
    )

    // OpenAI
    fun openai(apiKey: String, model: String = "gpt-4o") = ModelGateway(
        apiKey = apiKey,
        baseUrl = "https://api.openai.com/v1",
        model = model,
        protocol = ModelGateway.PROTOCOL_OPENAI
    )

    // Agnes
    fun agnes(apiKey: String, model: String = "agnes-2.5-flash") = ModelGateway(
        apiKey = apiKey,
        baseUrl = "https://apihub.agnes-ai.com/v1",
        model = model,
        protocol = ModelGateway.PROTOCOL_OPENAI
    )

    // 自定义端点：可指定协议族与额外请求头
    fun custom(
        apiKey: String,
        baseUrl: String,
        model: String,
        protocol: String = ModelGateway.PROTOCOL_OPENAI,
        customHeaders: Map<String, String> = emptyMap()
    ) = ModelGateway(
        apiKey = apiKey,
        baseUrl = baseUrl,
        model = model,
        protocol = protocol,
        customHeaders = customHeaders
    )
}
