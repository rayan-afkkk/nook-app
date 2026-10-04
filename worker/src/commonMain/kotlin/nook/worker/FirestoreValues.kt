package nook.worker

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Epoch millis from an RFC 3339 timestamp. */
expect fun parseIsoMillis(iso: String): Long

/**
 * Firestore REST value → plain Kotlin: String, Boolean, Long, Double, List, Map, or null.
 * Timestamps become epoch millis.
 */
fun decodeValue(v: JsonElement): Any? {
    val o = v as? JsonObject ?: return null
    val (type, raw) = o.entries.firstOrNull() ?: return null
    return when (type) {
        "nullValue" -> null
        "booleanValue" -> raw.jsonPrimitive.booleanOrNull
        "integerValue" -> raw.jsonPrimitive.content.toLongOrNull()
        "doubleValue" -> raw.jsonPrimitive.doubleOrNull
        "timestampValue" -> parseIsoMillis(raw.jsonPrimitive.content)
        "stringValue", "referenceValue", "bytesValue" -> raw.jsonPrimitive.content
        "arrayValue" -> ((raw as? JsonObject)?.get("values") as? JsonArray)?.map { decodeValue(it) } ?: emptyList<Any?>()
        "mapValue" -> decodeFields(((raw as? JsonObject)?.get("fields") as? JsonObject) ?: JsonObject(emptyMap()))
        else -> null
    }
}

fun decodeFields(fields: JsonObject): Map<String, Any?> = fields.mapValues { (_, v) -> decodeValue(v) }

/** Helpers for reading decoded documents. */
fun Map<String, Any?>?.str(key: String): String = this?.get(key) as? String ?: ""

fun Map<String, Any?>?.strings(key: String): List<String> = (this?.get(key) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>?.map(key: String): Map<String, Any?>? = this?.get(key) as? Map<String, Any?>

fun stringValue(s: String): JsonElement = JsonObject(mapOf("stringValue" to JsonPrimitive(s)))
