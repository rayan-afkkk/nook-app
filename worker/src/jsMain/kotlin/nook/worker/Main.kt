package nook.worker

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.promise
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.js.Promise

/*
 * Nook Worker (Kotlin/JS on Cloudflare Workers, service-worker format).
 * Endpoints — all POST, all need "Authorization: Bearer <Firebase ID token>":
 *   /notify          { chatId }               push "Ali sent you a photo" to the other members
 *   /call            { callId, action }       ring / cancel the callee (full-screen call)
 *   /livekit-token   { callId }               LiveKit join token for a call you're in
 *   /media/delete    { scope, assets[] }      delete Cloudinary assets you're allowed to touch
 * Cron: deletes expired disappearing messages + their media.
 *
 * PRIVACY: we never read, forward or log message content. Logs carry ids and counts only.
 */

private class Ctx(val uid: String, val projectId: String, val token: String, val fs: Firestore)

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    val self: dynamic = js("globalThis")
    self.addEventListener("fetch") { event: dynamic ->
        event.respondWith(GlobalScope.promise { handle(event.request) })
    }
    self.addEventListener("scheduled") { event: dynamic ->
        event.waitUntil(GlobalScope.promise { sweepExpired() })
    }
}

private suspend fun handle(request: dynamic): dynamic {
    val method = request.method as String
    val path = pathOf(request.url as String)
    if (method == "GET" && path == "/") return respond(buildJsonObject { put("ok", true); put("service", "nook-worker") })
    if (method != "POST") return respond(error("method not allowed"), 405)
    return try {
        val projectId = env("FIREBASE_PROJECT_ID")
        val uid = verifyIdToken(request.headers.get("authorization") as String?, projectId)
        val token = accessToken(parseServiceAccount(env("FIREBASE_SERVICE_ACCOUNT")))
        val ctx = Ctx(uid, projectId, token, Firestore(projectId, token))
        val text = (request.text() as Promise<String>).await()
        val body = runCatching { json.parseToJsonElement(text).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        fun field(k: String): String? = (body[k] as? JsonPrimitive)?.contentOrNull
        when (path) {
            "/notify" -> respond(notify(ctx, validId(field("chatId"))))
            "/call" -> respond(call(ctx, validId(field("callId")), field("action").orEmpty()))
            "/livekit-token" -> respond(liveKitTokenFor(ctx, validId(field("callId"))))
            "/media/delete" -> respond(deleteMedia(ctx, validId(field("scope")), body["assets"]))
            else -> respond(error("not found"), 404)
        }
    } catch (e: HttpError) {
        respond(error(e.message), e.status)
    } catch (e: Throwable) {
        console.error("worker error", e.message)
        respond(error("internal"), 500)
    }
}

// ---------------- handlers ----------------

private suspend fun notify(ctx: Ctx, chatId: String): JsonObject {
    val chat = ctx.fs.get("chats/$chatId")
    val members = chat.strings("members")
    if (chat == null || ctx.uid !in members) throw HttpError(403, "not a member")
    val last = chat.map("lastMessage")
    // The chat doc is written in the same batch as the message, so it tells us the type safely.
    if (last.str("senderId") != ctx.uid) throw HttpError(409, "stale")
    val type = last.str("type")
    if (type !in MESSAGE_TYPES) return count("sent", 0)

    val sender = ctx.fs.get("users/${ctx.uid}")
    val senderName = sender.str("displayName").ifEmpty { sender.str("username") }.ifEmpty { "Someone" }
    val text = notificationText(senderName, type, chat.str("type") == "group", chat.str("name"))

    val sent = coroutineScope {
        members.filter { it != ctx.uid }.map { member ->
            async {
                val settings = ctx.fs.get("users/$member/private/settings") ?: return@async 0
                if (settings["notificationsEnabled"] == false) return@async 0
                if (chatId in settings.strings("mutedChats")) return@async 0
                if (ctx.uid in settings.strings("blocked")) return@async 0
                pushAll(ctx, member, settings.strings("fcmTokens"), mapOf("type" to "message", "chatId" to chatId, "title" to text.title, "body" to text.body), 3600)
            }
        }.awaitAll().sum()
    }
    println("notify chat=$chatId recipients=${members.size - 1} sent=$sent")
    return count("sent", sent)
}

private suspend fun call(ctx: Ctx, callId: String, action: String): JsonObject {
    val c = ctx.fs.get("calls/$callId")
    if (c == null || c.str("callerId") != ctx.uid) throw HttpError(403, "not your call")
    val callee = c.str("calleeId")
    val settings = ctx.fs.get("users/$callee/private/settings")
    if (settings == null || ctx.uid in settings.strings("blocked")) return count("sent", 0)
    val tokens = settings.strings("fcmTokens")
    if (action == "cancel") return count("sent", pushAll(ctx, callee, tokens, mapOf("type" to "call_cancel", "callId" to callId), 60))
    if (c.str("status") != "ringing") return count("sent", 0)
    val caller = ctx.fs.get("users/${ctx.uid}")
    val callerName = caller.str("displayName").ifEmpty { caller.str("username") }.ifEmpty { "Someone" }
    val sent = pushAll(
        ctx, callee, tokens,
        mapOf("type" to "call", "callId" to callId, "callerName" to callerName, "callType" to c.str("type").ifEmpty { "voice" }), 45,
    )
    println("call ring call=$callId sent=$sent")
    return count("sent", sent)
}

private suspend fun liveKitTokenFor(ctx: Ctx, callId: String): JsonObject {
    val c = ctx.fs.get("calls/$callId")
    if (c == null || ctx.uid !in c.strings("members")) throw HttpError(403, "not in this call")
    if (c.str("status") != "ringing" && c.str("status") != "accepted") throw HttpError(410, "call is over")
    val me = ctx.fs.get("users/${ctx.uid}")
    val token = liveKitToken(
        env("LIVEKIT_API_KEY"), env("LIVEKIT_API_SECRET"), ctx.uid,
        me.str("displayName").ifEmpty { me.str("username") }.ifEmpty { "Nook" }, "call_$callId",
    )
    return buildJsonObject { put("token", token) }
}

private suspend fun deleteMedia(ctx: Ctx, scope: String, assetsRaw: JsonElement?): JsonObject {
    val prefix = if (scope == "avatar") {
        "nook/avatars/${ctx.uid}/"
    } else {
        val chat = ctx.fs.get("chats/$scope")
        if (chat == null || ctx.uid !in chat.strings("members")) throw HttpError(403, "not a member")
        "nook/chats/$scope/"
    }
    var deleted = 0
    for (a in (assetsRaw as? JsonArray).orEmpty().take(50)) {
        val o = a as? JsonObject ?: continue
        val publicId = o["publicId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val type = o["resourceType"]?.jsonPrimitive?.contentOrNull ?: "image"
        if (!isAllowedAsset(publicId, prefix) || type !in RESOURCE_TYPES) continue
        if (cloudinaryDestroy(env("CLOUDINARY_CLOUD_NAME"), env("CLOUDINARY_API_KEY"), env("CLOUDINARY_API_SECRET"), publicId, type)) deleted++
    }
    return count("deleted", deleted)
}

/** Cron sweep: expired disappearing messages, plus their media. */
private suspend fun sweepExpired() {
    try {
        val projectId = env("FIREBASE_PROJECT_ID")
        val fs = Firestore(projectId, accessToken(parseServiceAccount(env("FIREBASE_SERVICE_ACCOUNT"))))
        val nowIso = js("new Date().toISOString()") as String
        val rows = fs.expiredMessages(nowIso, 200)
        var media = 0
        for ((path, data) in rows) {
            val chatId = path.split("/").getOrNull(1).orEmpty()
            val m = data.map("media")
            val publicId = m.str("publicId")
            if (publicId.isNotEmpty() && isAllowedAsset(publicId, "nook/chats/$chatId/")) {
                val type = m.str("resourceType").ifEmpty { "image" }
                if (cloudinaryDestroy(env("CLOUDINARY_CLOUD_NAME"), env("CLOUDINARY_API_KEY"), env("CLOUDINARY_API_SECRET"), publicId, type)) media++
            }
            fs.delete(path)
        }
        println("sweep messages=${rows.size} media=$media")
    } catch (e: Throwable) {
        console.error("sweep failed", e.message)
    }
}

// ---------------- helpers ----------------

/** "https://host/notify?x=1" → "/notify" */
private fun pathOf(url: String): String {
    val afterScheme = url.substringAfter("://")
    val slash = afterScheme.indexOf('/')
    return if (slash < 0) "/" else afterScheme.substring(slash).substringBefore('?').substringBefore('#')
}

private suspend fun pushAll(ctx: Ctx, uid: String, tokens: List<String>, data: Map<String, String>, ttl: Int): Int {
    var ok = 0
    val dead = mutableListOf<String>()
    for (t in tokens.take(10)) {
        when (sendFcm(ctx.projectId, ctx.token, t, data, ttl)) {
            SendResult.Ok -> ok++
            SendResult.Unregistered -> dead += t
            SendResult.Error -> Unit
        }
    }
    if (dead.isNotEmpty()) runCatching { ctx.fs.arrayRemove("users/$uid/private/settings", "fcmTokens", dead) }
    return ok
}

private fun count(key: String, n: Int) = buildJsonObject { put(key, n) }

private fun error(message: String) = buildJsonObject { put("error", message) }

private fun respond(body: JsonObject, status: Int = 200): dynamic {
    val text = body.toString()
    val init = jsObject("status" to status, "headers" to jsObject("content-type" to "application/json"))
    return js("new Response(text, init)")
}
