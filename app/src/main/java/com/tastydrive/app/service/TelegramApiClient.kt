package com.tastydrive.app.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tastydrive.app.data.MediaItem
import com.tastydrive.app.util.NativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

class TelegramApiClient(private val context: Context) {

    // OkHttpClient with generous timeouts to support multi-gigabyte local docker uploads
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun testConnection(
        token: String,
        baseUrl: String = CLOUD_API_BASE
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            return@withContext ApiResult.Error("Bot Token is empty!")
        }

        val formattedBase = normalizeBaseUrl(baseUrl)
        val url = "$formattedBase$cleanToken/getMe"
        val request = Request.Builder().url(url).get().build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JsonParser.parseString(bodyStr).asJsonObject
                    if (json.get("ok")?.asBoolean == true) {
                        val result = json.getAsJsonObject("result")
                        val username = result.get("username")?.asString ?: "Unknown"
                        val firstName = result.get("first_name")?.asString ?: "Bot"
                        return@withContext ApiResult.Success("Connected: $firstName (@$username)")
                    }
                }
                return@withContext ApiResult.Error("Invalid response from server: $bodyStr", response.code)
            }
        } catch (e: Exception) {
            return@withContext ApiResult.Error("Network error: ${e.localizedMessage}")
        }
    }

    suspend fun testChannel(
        token: String,
        channelId: String,
        baseUrl: String = CLOUD_API_BASE
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        val cleanChatId = channelId.trim()
        if (cleanToken.isEmpty() || cleanChatId.isEmpty()) {
            return@withContext ApiResult.Error("Token or Channel ID is missing!")
        }

        val formattedBase = normalizeBaseUrl(baseUrl)
        val url = "$formattedBase$cleanToken/getChat?chat_id=$cleanChatId"
        val request = Request.Builder().url(url).get().build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JsonParser.parseString(bodyStr).asJsonObject
                    if (json.get("ok")?.asBoolean == true) {
                        val result = json.getAsJsonObject("result")
                        val title = result.get("title")?.asString
                            ?: result.get("username")?.asString
                            ?: result.get("first_name")?.asString
                            ?: "Connected Chat"
                        return@withContext ApiResult.Success("Channel Verified: $title")
                    }
                }
                return@withContext ApiResult.Error("Could not verify channel ($cleanChatId). Ensure bot is an Admin in the channel!", response.code)
            }
        } catch (e: Exception) {
            return@withContext ApiResult.Error("Channel check failed: ${e.localizedMessage}")
        }
    }

    suspend fun uploadMedia(
        token: String,
        channelId: String,
        item: MediaItem,
        baseUrl: String = CLOUD_API_BASE,
        isLocalApi: Boolean = false,
        onProgress: (percent: Int, bytesWritten: Long, totalBytes: Long) -> Unit
    ): ApiResult<Boolean> = withContext(Dispatchers.IO) {
        // Enforce file size constraints
        if (!isLocalApi && item.isOver50MB) {
            return@withContext ApiResult.Error("File is > 50MB. Official Telegram Cloud Bot API limit is 50MB. Waiting for Local Docker API or share via Telegram App.")
        }
        if (isLocalApi && item.isOver2GB) {
            return@withContext ApiResult.Error("File is > 2000MB (2GB). Telegram's maximum file size limit is 2GB.")
        }

        val cleanToken = token.trim()
        val cleanChatId = channelId.trim()
        val mediaType = (item.mimeType.ifEmpty { "application/octet-stream" }).toMediaTypeOrNull()

        val countingBody = CountingRequestBody(
            inputStreamProvider = {
                try {
                    if (item.uri.scheme == "file") {
                        java.io.FileInputStream(java.io.File(item.uri.path ?: item.path ?: ""))
                    } else {
                        context.contentResolver.openInputStream(item.uri)
                    }
                } catch (e: Exception) {
                    if (!item.path.isNullOrEmpty()) {
                        java.io.FileInputStream(java.io.File(item.path))
                    } else {
                        throw e
                    }
                }
            },
            mediaType = mediaType,
            contentLength = item.size,
            onProgressUpdate = { bytesWritten, totalBytes ->
                val pct = if (totalBytes > 0) ((bytesWritten * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                onProgress(pct, bytesWritten, totalBytes)
            }
        )

        val sizeFormatted = NativeEngine.safeFormatFileSize(item.size)
        val apiTag = if (isLocalApi) "[Local Docker 🚀]" else "[Cloud ☁️]"
        val caption = "Tasty Drive $apiTag: ${item.displayName} (${sizeFormatted})"

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", cleanChatId)
            .addFormDataPart("caption", caption)
            .addFormDataPart(
                "document",
                item.displayName,
                countingBody
            )

        val formattedBase = normalizeBaseUrl(baseUrl)
        val url = "$formattedBase$cleanToken/sendDocument"

        val request = Request.Builder()
            .url(url)
            .post(multipartBuilder.build())
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JsonParser.parseString(bodyStr).asJsonObject
                    if (json.get("ok")?.asBoolean == true) {
                        return@withContext ApiResult.Success(true)
                    }
                }

                if (response.code == 429) {
                    val retrySec = try {
                        val json = JsonParser.parseString(bodyStr).asJsonObject
                        json.getAsJsonObject("parameters")?.get("retry_after")?.asInt ?: 5
                    } catch (e: Exception) { 5 }
                    Log.w("TelegramApiClient", "Telegram rate limit 429. Sleeping for $retrySec s before retry...")
                    kotlinx.coroutines.delay((retrySec + 1) * 1000L)
                    client.newCall(request).execute().use { retryResp ->
                        val retryBody = retryResp.body?.string() ?: ""
                        if (retryResp.isSuccessful) {
                            val json = JsonParser.parseString(retryBody).asJsonObject
                            if (json.get("ok")?.asBoolean == true) {
                                return@withContext ApiResult.Success(true)
                            }
                        }
                    }
                }

                // Check Telegram rate limits or error
                val errorMsg = try {
                    val json = JsonParser.parseString(bodyStr).asJsonObject
                    json.get("description")?.asString ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: $bodyStr"
                }

                Log.e("TelegramApiClient", "Upload error for ${item.displayName}: $errorMsg")
                return@withContext ApiResult.Error(errorMsg, response.code)
            }
        } catch (e: IOException) {
            return@withContext ApiResult.Error("Upload interrupted: ${e.localizedMessage}")
        } catch (e: Exception) {
            return@withContext ApiResult.Error("Upload error: ${e.localizedMessage}")
        }
    }

    companion object {
        const val CLOUD_API_BASE = "https://api.telegram.org/bot"

        fun normalizeBaseUrl(serverUrl: String): String {
            val trimmed = serverUrl.trim()
            if (trimmed.isEmpty() || trimmed.equals(CLOUD_API_BASE, ignoreCase = true)) {
                return CLOUD_API_BASE
            }

            var clean = trimmed
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                clean = "http://$clean"
            }
            clean = clean.removeSuffix("/")
            return if (clean.endsWith("/bot")) "$clean" else "$clean/bot"
        }
    }
}
