package com.nook.app.data.remote

import com.nook.app.AppConfig
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class MediaAsset(val publicId: String, val resourceType: String)

/**
 * Client for the Cloudflare Worker in /worker. Every call carries the Firebase ID token;
 * the Worker verifies it and chat membership. We only ever send ids — never message content.
 */
class WorkerApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val idToken: suspend () -> String?,
) {
    private val jsonType = "application/json".toMediaType()

    private suspend fun post(path: String, body: JsonObject): JsonObject {
        check(AppConfig.hasWorker) { "Worker URL isn't configured (NOOK_WORKER_URL)." }
        val token = idToken() ?: error("Not signed in")
        val request = Request.Builder()
            .url(AppConfig.workerUrl + path)
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).await().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw HttpException(res.code, "Worker $path failed (${res.code})")
            return if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text).jsonObject
        }
    }

    /** Retries with exponential backoff (1s, 2s, 4s). Client errors (4xx) are not retried. */
    private suspend fun <T> withRetry(attempts: Int = 4, block: suspend () -> T): T {
        var wait = 1000L
        var last: Throwable? = null
        repeat(attempts) { i ->
            try {
                return block()
            } catch (e: HttpException) {
                if (e.code in 400..499) throw e
                last = e
            } catch (e: java.io.IOException) {
                last = e
            }
            if (i < attempts - 1) { delay(wait); wait *= 2 }
        }
        throw last ?: IllegalStateException("retry failed")
    }

    suspend fun notifyMessage(chatId: String) {
        withRetry { post("/notify", buildJsonObject { put("chatId", chatId) }) }
    }

    suspend fun ringCall(callId: String) {
        withRetry(3) { post("/call", buildJsonObject { put("callId", callId); put("action", "ring") }) }
    }

    suspend fun cancelCall(callId: String) {
        withRetry(2) { post("/call", buildJsonObject { put("callId", callId); put("action", "cancel") }) }
    }

    suspend fun liveKitToken(callId: String): String = withRetry(3) {
        post("/livekit-token", buildJsonObject { put("callId", callId) })["token"]?.jsonPrimitive?.content
            ?: error("No token returned")
    }

    /** scope = a chat id (assets must live in nook/chats/{chatId}/) or "avatar". */
    suspend fun deleteMedia(scope: String, assets: List<MediaAsset>) {
        if (assets.isEmpty()) return
        val array = JsonArray(
            assets.map {
                JsonObject(mapOf("publicId" to JsonPrimitive(it.publicId), "resourceType" to JsonPrimitive(it.resourceType)))
            },
        )
        withRetry(3) {
            post("/media/delete", JsonObject(mapOf("scope" to JsonPrimitive(scope), "assets" to array)))
        }
    }
}
