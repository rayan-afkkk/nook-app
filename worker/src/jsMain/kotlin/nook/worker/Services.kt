package nook.worker

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

val json = Json { ignoreUnknownKeys = true }

// ---------- Firebase ID token verification ----------

private const val JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
private var jwksCache: Pair<Long, Map<String, String>>? = null

private suspend fun jwks(): Map<String, String> {
    val now = nowSeconds()
    jwksCache?.let { (exp, keys) -> if (exp > now) return keys }
    val res = httpFetch(JWKS_URL)
    if (!res.ok) throw HttpError(503, "jwks unavailable")
    val keys = json.parseToJsonElement(res.text).jsonObject["keys"]?.jsonArray.orEmpty()
        .associate { k -> k.jsonObject["kid"]!!.jsonPrimitive.content to k.toString() }
    jwksCache = (now + 3600) to keys
    return keys
}

/** Verifies "Bearer <Firebase ID token>" and returns the caller's uid. */
suspend fun verifyIdToken(header: String?, projectId: String): String {
    if (header == null || !header.startsWith("Bearer ")) throw HttpError(401, "missing token")
    val parts = header.removePrefix("Bearer ").trim().split(".")
    if (parts.size != 3) throw HttpError(401, "invalid token")
    try {
        val head = json.parseToJsonElement(base64UrlDecode(parts[0]).decodeToString()).jsonObject
        val payload = json.parseToJsonElement(base64UrlDecode(parts[1]).decodeToString()).jsonObject
        if (head["alg"]?.jsonPrimitive?.content != "RS256") throw HttpError(401, "invalid token")
        val kid = head["kid"]?.jsonPrimitive?.content ?: throw HttpError(401, "invalid token")
        val jwk = jwks()[kid] ?: throw HttpError(401, "invalid token")
        if (!rs256Verify(jwk, parts[0] + "." + parts[1], base64UrlDecode(parts[2]))) throw HttpError(401, "invalid token")
        return firebaseClaimsUid(payload, projectId, nowSeconds()) ?: throw HttpError(401, "invalid token")
    } catch (e: HttpError) {
        throw e
    } catch (e: Throwable) {
        throw HttpError(401, "invalid token")
    }
}

// ---------- Google OAuth (service account) ----------

class ServiceAccount(val projectId: String, val clientEmail: String, val privateKey: String)

fun parseServiceAccount(raw: String): ServiceAccount {
    val o = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: throw IllegalStateException("FIREBASE_SERVICE_ACCOUNT is not JSON")
    fun s(k: String) = o[k]?.jsonPrimitive?.content.orEmpty()
    if (s("client_email").isEmpty() || s("private_key").isEmpty()) throw IllegalStateException("FIREBASE_SERVICE_ACCOUNT is incomplete")
    return ServiceAccount(s("project_id"), s("client_email"), s("private_key"))
}

private const val SCOPES = "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging"
private var tokenCache: Pair<Long, String>? = null

suspend fun accessToken(sa: ServiceAccount): String {
    val now = nowSeconds()
    tokenCache?.let { (exp, token) -> if (exp - 60 > now) return token }
    val header = buildJsonObject { put("alg", "RS256"); put("typ", "JWT") }
    val input = jwtSigningInput(header, googleAssertionClaims(sa.clientEmail, SCOPES, now))
    val assertion = input + "." + base64UrlEncode(rs256Sign(sa.privateKey, input))
    val res = httpFetch(
        "https://oauth2.googleapis.com/token", "POST",
        mapOf("content-type" to "application/x-www-form-urlencoded"),
        formEncode(mapOf("grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion" to assertion)),
    )
    if (!res.ok) throw IllegalStateException("token exchange failed (${res.status})")
    val o = json.parseToJsonElement(res.text).jsonObject
    val token = o["access_token"]!!.jsonPrimitive.content
    val expires = o["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600
    tokenCache = (now + expires) to token
    return token
}

// ---------- Firestore REST (admin credentials bypass rules, so callers check membership) ----------

class Firestore(private val projectId: String, private val token: String) {
    private val root = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"
    private val headers get() = mapOf("authorization" to "Bearer $token", "content-type" to "application/json")

    suspend fun get(path: String): Map<String, Any?>? {
        val res = httpFetch("$root/$path", headers = headers)
        if (res.status == 404) return null
        if (!res.ok) throw IllegalStateException("firestore get failed (${res.status})")
        val fields = json.parseToJsonElement(res.text).jsonObject["fields"] as? JsonObject ?: JsonObject(emptyMap())
        return decodeFields(fields)
    }

    suspend fun delete(path: String) {
        val res = httpFetch("$root/$path", "DELETE", headers)
        if (!res.ok && res.status != 404) throw IllegalStateException("firestore delete failed (${res.status})")
    }

    /** Removes values from an array field without reading first. */
    suspend fun arrayRemove(path: String, field: String, values: List<String>) {
        val body = buildJsonObject {
            put("writes", buildJsonArray {
                add(buildJsonObject {
                    putJsonObject("transform") {
                        put("document", "projects/$projectId/databases/(default)/documents/$path")
                        put("fieldTransforms", buildJsonArray {
                            add(buildJsonObject {
                                put("fieldPath", field)
                                putJsonObject("removeAllFromArray") { put("values", JsonArray(values.map { stringValue(it) })) }
                            })
                        })
                    }
                })
            })
        }
        val res = httpFetch("https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents:commit", "POST", headers, body.toString())
        if (!res.ok) throw IllegalStateException("firestore commit failed (${res.status})")
    }

    /** Collection-group query for messages whose expireAt has passed. Returns (docPath, data). */
    suspend fun expiredMessages(nowIso: String, limit: Int): List<Pair<String, Map<String, Any?>>> {
        val body = buildJsonObject {
            putJsonObject("structuredQuery") {
                put("from", buildJsonArray { add(buildJsonObject { put("collectionId", "messages"); put("allDescendants", true) }) })
                putJsonObject("where") {
                    putJsonObject("fieldFilter") {
                        putJsonObject("field") { put("fieldPath", "expireAt") }
                        put("op", "LESS_THAN")
                        putJsonObject("value") { put("timestampValue", nowIso) }
                    }
                }
                put("limit", limit)
            }
        }
        val res = httpFetch("$root:runQuery", "POST", headers, body.toString())
        if (!res.ok) throw IllegalStateException("firestore query failed (${res.status})")
        val prefix = "projects/$projectId/databases/(default)/documents/"
        return json.parseToJsonElement(res.text).jsonArray.mapNotNull { row ->
            val doc = row.jsonObject["document"] as? JsonObject ?: return@mapNotNull null
            val name = doc["name"]!!.jsonPrimitive.content.removePrefix(prefix)
            name to decodeFields(doc["fields"] as? JsonObject ?: JsonObject(emptyMap()))
        }
    }
}

// ---------- FCM HTTP v1 ----------

enum class SendResult { Ok, Unregistered, Error }

/** Data-only, high priority, so the app renders its own content-free notification or full-screen call. */
suspend fun sendFcm(projectId: String, token: String, device: String, data: Map<String, String>, ttlSeconds: Int): SendResult {
    val body = buildJsonObject {
        putJsonObject("message") {
            put("token", device)
            put("data", JsonObject(data.mapValues { JsonPrimitive(it.value) }))
            putJsonObject("android") { put("priority", "HIGH"); put("ttl", "${ttlSeconds}s") }
        }
    }
    val res = httpFetch(
        "https://fcm.googleapis.com/v1/projects/$projectId/messages:send", "POST",
        mapOf("authorization" to "Bearer $token", "content-type" to "application/json"), body.toString(),
    )
    return when {
        res.ok -> SendResult.Ok
        res.status == 404 -> SendResult.Unregistered
        res.status == 400 && ("UNREGISTERED" in res.text || "not a valid FCM registration token" in res.text) -> SendResult.Unregistered
        else -> SendResult.Error
    }
}

// ---------- LiveKit & Cloudinary ----------

suspend fun liveKitToken(apiKey: String, apiSecret: String, identity: String, name: String, room: String): String {
    val header = buildJsonObject { put("alg", "HS256"); put("typ", "JWT") }
    val input = jwtSigningInput(header, liveKitClaims(apiKey, identity, name, room, nowSeconds()))
    return input + "." + base64UrlEncode(hmacSha256(apiSecret, input))
}

/** Signed Admin "destroy" (the unsigned upload preset can't delete). */
suspend fun cloudinaryDestroy(cloud: String, apiKey: String, apiSecret: String, publicId: String, resourceType: String): Boolean {
    if (resourceType !in RESOURCE_TYPES || cloud.isEmpty() || apiSecret.isEmpty()) return false
    val timestamp = nowSeconds().toString()
    val signature = sha1Hex(cloudinaryStringToSign(mapOf("public_id" to publicId, "timestamp" to timestamp), apiSecret))
    val res = httpFetch(
        "https://api.cloudinary.com/v1_1/$cloud/$resourceType/destroy", "POST",
        mapOf("content-type" to "application/x-www-form-urlencoded"),
        formEncode(mapOf("public_id" to publicId, "timestamp" to timestamp, "api_key" to apiKey, "signature" to signature)),
    )
    return res.ok
}
