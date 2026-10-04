package nook.rules

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.Proxy
import java.time.Instant
import java.util.Base64
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tiny REST client for the Firestore and Realtime Database emulators. Users are authenticated with
 * unsigned emulator tokens; "owner" bypasses rules (used only for seeding/clearing).
 */
object Emulator {
    const val PROJECT = "demo-nook"
    private const val RTDB_NS = "$PROJECT-default-rtdb"
    private val firestoreHost = System.getenv("FIRESTORE_EMULATOR_HOST") ?: "127.0.0.1:8080"
    private val databaseHost = System.getenv("FIREBASE_DATABASE_EMULATOR_HOST") ?: "127.0.0.1:9000"
    val client: OkHttpClient = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
    private val jsonType = "application/json".toMediaType()

    const val OWNER = "owner"

    /** Unsigned ID token in the shape the emulators accept. */
    fun token(uid: String): String {
        if (uid == OWNER) return OWNER
        val enc = Base64.getUrlEncoder().withoutPadding()
        val now = Instant.now().epochSecond
        val header = """{"alg":"none","kid":"fakekid","typ":"JWT"}"""
        val payload = buildJsonObject {
            put("iss", "https://securetoken.google.com/$PROJECT"); put("aud", PROJECT)
            put("iat", now); put("exp", now + 3600); put("auth_time", now)
            put("sub", uid); put("user_id", uid)
            put("firebase", buildJsonObject { put("sign_in_provider", "custom"); put("identities", JsonObject(emptyMap())) })
        }
        return enc.encodeToString(header.toByteArray()) + "." + enc.encodeToString(payload.toString().toByteArray()) + "."
    }

    fun call(method: String, url: String, uid: String?, body: String? = null, authInQuery: Boolean = false): Int {
        // The RTDB REST API takes user tokens as ?auth=…; "owner" (admin) and Firestore use the header.
        val finalUrl = if (authInQuery && uid != null && uid != OWNER) "$url&auth=${token(uid)}" else url
        val req = Request.Builder().url(finalUrl).method(method, body?.toRequestBody(jsonType)).apply {
            if (uid != null && (!authInQuery || uid == OWNER)) header("Authorization", "Bearer ${token(uid)}")
        }.build()
        client.newCall(req).execute().use { return it.code }
    }

    // ---------- Firestore ----------

    private val docsRoot get() = "http://$firestoreHost/v1/projects/$PROJECT/databases/(default)/documents"
    fun docName(path: String) = "projects/$PROJECT/databases/(default)/documents/$path"

    fun clearFirestore() {
        call("DELETE", "http://$firestoreHost/emulator/v1/projects/$PROJECT/databases/(default)/documents", null)
    }

    fun fsGet(uid: String?, path: String) = call("GET", "$docsRoot/$path", uid)
    fun fsDelete(uid: String?, path: String) = call("DELETE", "$docsRoot/$path", uid)
    fun fsCommit(uid: String?, vararg writes: JsonObject) =
        call("POST", "$docsRoot:commit", uid, buildJsonObject { put("writes", JsonArray(writes.toList())) }.toString())

    /** [parent] is "" for root collections or e.g. "chats/abc" for sub-collections. */
    fun fsQuery(uid: String?, parent: String, query: JsonObject) =
        call("POST", (if (parent.isEmpty()) docsRoot else "$docsRoot/$parent") + ":runQuery", uid, buildJsonObject { put("structuredQuery", query) }.toString())

    // ---------- Realtime Database ----------

    private fun dbUrl(path: String) = "http://$databaseHost/$path.json?ns=$RTDB_NS"
    fun dbSet(uid: String?, path: String, value: JsonElement) = call("PUT", dbUrl(path), uid, value.toString(), authInQuery = true)
    fun dbUpdate(uid: String?, path: String, value: JsonObject) = call("PATCH", dbUrl(path), uid, value.toString(), authInQuery = true)
    fun dbGet(uid: String?, path: String) = call("GET", dbUrl(path), uid, authInQuery = true)
    fun dbRemove(uid: String?, path: String) = call("DELETE", dbUrl(path), uid, authInQuery = true)
    fun clearDatabase() { dbSet(OWNER, "", JsonNull) }

    /** Loads firebase/database.rules.json into the emulator namespace the tests use. */
    fun loadDatabaseRules() {
        val rules = java.io.File("database.rules.json").readText()
        val status = call("PUT", "http://$databaseHost/.settings/rules.json?ns=$RTDB_NS", OWNER, rules)
        check(status in 200..299) { "Couldn't load database rules (HTTP $status)" }
    }
    val serverTimestamp: JsonElement = JsonObject(mapOf(".sv" to JsonPrimitive("timestamp")))
}

// ---------- Firestore value encoding ----------

/** Server request-time marker for transforms, and a fixed timestamp value. */
object ServerTime
data class Ts(val millis: Long)

fun encode(v: Any?): JsonElement = when (v) {
    null -> JsonObject(mapOf("nullValue" to JsonNull))
    is String -> JsonObject(mapOf("stringValue" to JsonPrimitive(v)))
    is Boolean -> JsonObject(mapOf("booleanValue" to JsonPrimitive(v)))
    is Int, is Long -> JsonObject(mapOf("integerValue" to JsonPrimitive(v.toString())))
    is Ts -> JsonObject(mapOf("timestampValue" to JsonPrimitive(Instant.ofEpochMilli(v.millis).toString())))
    is List<*> -> JsonObject(mapOf("arrayValue" to JsonObject(mapOf("values" to JsonArray(v.map(::encode))))))
    is Map<*, *> -> JsonObject(mapOf("mapValue" to JsonObject(mapOf("fields" to fields(v)))))
    else -> error("can't encode $v")
}

private fun fields(m: Map<*, *>): JsonObject =
    JsonObject(m.filterValues { it !is ServerTime }.entries.associate { (k, v) -> k.toString() to encode(v) })

private fun serverTransforms(m: Map<*, *>, prefix: String = ""): List<JsonObject> =
    m.entries.flatMap { (k, v) ->
        when (v) {
            is ServerTime -> listOf(buildJsonObject { put("fieldPath", prefix + k); put("setToServerValue", "REQUEST_TIME") })
            else -> emptyList()
        }
    }

/** Full overwrite, like DocumentReference.set(). ServerTime values become request-time transforms. */
fun setWrite(path: String, data: Map<String, Any?>): JsonObject = buildJsonObject {
    put("update", buildJsonObject { put("name", Emulator.docName(path)); put("fields", fields(data)) })
    put("updateTransforms", JsonArray(serverTransforms(data)))
}

/**
 * Partial update, like DocumentReference.update(). Keys may be dotted field paths ("lastRead.uid").
 * [deletes] removes fields; [arrayUnion]/[arrayRemove] are array transforms.
 */
fun updateWrite(
    path: String,
    data: Map<String, Any?> = emptyMap(),
    deletes: List<String> = emptyList(),
    arrayUnion: Map<String, List<Any?>> = emptyMap(),
    arrayRemove: Map<String, List<Any?>> = emptyMap(),
): JsonObject {
    // Expand dotted keys into nested maps for the document body.
    val nested = mutableMapOf<String, Any?>()
    val transforms = mutableListOf<JsonObject>()
    val mask = mutableListOf<String>()
    for ((k, v) in data) {
        if (v is ServerTime) {
            transforms += buildJsonObject { put("fieldPath", k); put("setToServerValue", "REQUEST_TIME") }
            continue
        }
        mask += k
        val parts = k.split(".")
        var cur = nested
        for (p in parts.dropLast(1)) {
            @Suppress("UNCHECKED_CAST")
            cur = cur.getOrPut(p) { mutableMapOf<String, Any?>() } as MutableMap<String, Any?>
        }
        cur[parts.last()] = v
    }
    mask += deletes
    arrayUnion.forEach { (k, vs) -> transforms += buildJsonObject { put("fieldPath", k); put("appendMissingElements", buildJsonObject { put("values", JsonArray(vs.map(::encode))) }) } }
    arrayRemove.forEach { (k, vs) -> transforms += buildJsonObject { put("fieldPath", k); put("removeAllFromArray", buildJsonObject { put("values", JsonArray(vs.map(::encode))) }) } }
    return buildJsonObject {
        put("update", buildJsonObject { put("name", Emulator.docName(path)); put("fields", fields(nested)) })
        put("updateMask", buildJsonObject { put("fieldPaths", JsonArray(mask.map(::JsonPrimitive))) })
        put("updateTransforms", JsonArray(transforms))
        put("currentDocument", buildJsonObject { put("exists", true) })
    }
}

fun structuredQuery(collection: String, allDescendants: Boolean = false, where: JsonObject? = null, orderBy: String? = null): JsonObject =
    Json.parseToJsonElement(
        buildString {
            append("""{"from":[{"collectionId":"$collection","allDescendants":$allDescendants}]""")
            if (where != null) append(""","where":$where""")
            if (orderBy != null) append(""","orderBy":[{"field":{"fieldPath":"$orderBy"},"direction":"DESCENDING"}]""")
            append("}")
        },
    ) as JsonObject

fun fieldFilter(field: String, op: String, value: Any?): JsonObject = buildJsonObject {
    put("fieldFilter", buildJsonObject {
        put("field", buildJsonObject { put("fieldPath", field) })
        put("op", op)
        put("value", encode(value))
    })
}

// ---------- assertions ----------

fun assertAllowed(status: Int, what: String = "request") {
    if (status !in 200..299) fail("Expected $what to be allowed, got HTTP $status")
}

fun assertDenied(status: Int, what: String = "request") {
    assertTrue(status == 401 || status == 403, "Expected $what to be denied, got HTTP $status")
}
