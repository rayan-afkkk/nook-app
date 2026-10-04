package nook.worker

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
private val B64URL = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

@OptIn(ExperimentalEncodingApi::class)
private val B64 = Base64.Default

@OptIn(ExperimentalEncodingApi::class)
fun base64UrlEncode(bytes: ByteArray): String = B64URL.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
fun base64UrlDecode(s: String): ByteArray = B64URL.decode(s)

@OptIn(ExperimentalEncodingApi::class)
fun base64Decode(s: String): ByteArray = B64.decode(s)

fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

/** "header.payload" — the part of a JWT that gets signed. */
fun jwtSigningInput(header: JsonObject, payload: JsonObject): String =
    base64UrlEncode(header.toString().encodeToByteArray()) + "." + base64UrlEncode(payload.toString().encodeToByteArray())

/** LiveKit access-token claims allowing one identity into one room. */
fun liveKitClaims(apiKey: String, identity: String, name: String, room: String, nowSeconds: Long, ttlSeconds: Long = 7200): JsonObject =
    buildJsonObject {
        put("iss", apiKey)
        put("sub", identity)
        put("name", name)
        put("nbf", nowSeconds - 10)
        put("iat", nowSeconds)
        put("exp", nowSeconds + ttlSeconds)
        put("jti", "$identity-$nowSeconds")
        putJsonObject("video") {
            put("room", room)
            put("roomJoin", true)
            put("canPublish", true)
            put("canSubscribe", true)
            put("canPublishData", true)
        }
    }

/** Claims for the service-account → OAuth token exchange (JWT bearer grant). */
fun googleAssertionClaims(clientEmail: String, scope: String, nowSeconds: Long): JsonObject = buildJsonObject {
    put("iss", clientEmail)
    put("sub", clientEmail)
    put("aud", "https://oauth2.googleapis.com/token")
    put("scope", scope)
    put("iat", nowSeconds)
    put("exp", nowSeconds + 3600)
}

/** Checks the non-crypto parts of a Firebase ID token. Returns the uid or null. */
fun firebaseClaimsUid(payload: JsonObject, projectId: String, nowSeconds: Long): String? {
    fun s(k: String) = (payload[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
    fun n(k: String) = s(k)?.toDoubleOrNull()?.toLong()
    if (s("iss") != "https://securetoken.google.com/$projectId") return null
    if (s("aud") != projectId) return null
    val exp = n("exp") ?: return null
    val iat = n("iat") ?: return null
    if (exp < nowSeconds - 30 || iat > nowSeconds + 300) return null
    return s("sub")?.takeIf { it.isNotEmpty() && it.length <= 128 }
}
