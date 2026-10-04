package nook.worker

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkerLogicTest {

    @Test fun `notification text says who and what kind, never content`() {
        assertEquals(NotificationText("Nook", "Ali sent you a message"), notificationText("Ali Khan", "text", false, null))
        assertEquals("Ali sent you a photo", notificationText("Ali", "image", false, null).body)
        assertEquals("Ali sent you a voice message", notificationText("Ali", "voice", false, null).body)
        assertEquals(NotificationText("The Boys", "New message in The Boys"), notificationText("Ali", "gif", true, "The Boys"))
        assertEquals("Someone sent you a message", notificationText("  ", "weird", false, null).body)
        assertEquals("New message in your group", notificationText("A", "text", true, "").body)
    }

    @Test fun `firestore REST values decode to plain Kotlin`() {
        val fields = Json.parseToJsonElement(
            """
            {"members":{"arrayValue":{"values":[{"stringValue":"a"},{"stringValue":"b"}]}},
             "lastMessage":{"mapValue":{"fields":{"senderId":{"stringValue":"a"},"type":{"stringValue":"image"}}}},
             "notificationsEnabled":{"booleanValue":false},
             "count":{"integerValue":"3"},
             "at":{"timestampValue":"2026-01-01T00:00:00Z"},
             "empty":{"arrayValue":{}},
             "nothing":{"nullValue":null}}
            """,
        ).jsonObject
        val d = decodeFields(fields)
        assertEquals(listOf("a", "b"), d.strings("members"))
        assertEquals("image", d.map("lastMessage").str("type"))
        assertEquals(false, d["notificationsEnabled"])
        assertEquals(3L, d["count"])
        assertEquals(1767225600000L, d["at"])
        assertEquals(emptyList<Any?>(), d["empty"])
        assertNull(d["nothing"])
    }

    @Test fun `ids reject path tricks`() {
        assertEquals("dm_abc_DEF", validId("dm_abc_DEF"))
        listOf("chats/x", "../x", "", 42, null).forEach { bad -> assertFailsWith<HttpError> { validId(bad) } }
    }

    @Test fun `media deletes stay inside the caller's folder`() {
        assertTrue(isAllowedAsset("nook/chats/abc/photo1", "nook/chats/abc/"))
        assertFalse(isAllowedAsset("nook/chats/xyz/photo1", "nook/chats/abc/"))
        assertFalse(isAllowedAsset("nook/chats/abc/../xyz/p", "nook/chats/abc/"))
        assertFalse(isAllowedAsset("nook/avatars/u2/a", "nook/avatars/u1/"))
    }

    @Test fun `cloudinary signature input is sorted and matches a known SHA-1`() {
        val input = cloudinaryStringToSign(mapOf("timestamp" to "1700000000", "public_id" to "nook/chats/abc/x1"), "shh-secret")
        assertEquals("public_id=nook/chats/abc/x1&timestamp=1700000000shh-secret", input)
        val sha1 = hex(MessageDigest.getInstance("SHA-1").digest(input.encodeToByteArray()))
        assertEquals("91d4f3795f6ec868785e8ee9d7245d01a18926db", sha1)
    }

    @Test fun `livekit claims grant exactly one room for two hours`() {
        val c = liveKitClaims("APIkey", "u1", "Ali", "call_c1", nowSeconds = 1_000)
        assertEquals("APIkey", c["iss"]!!.jsonPrimitive.content)
        assertEquals("u1", c["sub"]!!.jsonPrimitive.content)
        assertEquals(7200, c["exp"]!!.jsonPrimitive.long - c["iat"]!!.jsonPrimitive.long)
        val video = c["video"]!!.jsonObject
        assertEquals("call_c1", video["room"]!!.jsonPrimitive.content)
        assertEquals("true", video["roomJoin"]!!.jsonPrimitive.content)
    }

    @Test fun `firebase token claims are checked for issuer, audience and expiry`() {
        fun claims(iss: String = "https://securetoken.google.com/p1", aud: String = "p1", exp: Long = 2_000, sub: String = "u1"): JsonObject =
            buildJsonObject { put("iss", iss); put("aud", aud); put("iat", 900); put("exp", exp); put("sub", sub) }
        assertEquals("u1", firebaseClaimsUid(claims(), "p1", nowSeconds = 1_000))
        assertNull(firebaseClaimsUid(claims(aud = "other"), "p1", 1_000))
        assertNull(firebaseClaimsUid(claims(iss = "https://evil"), "p1", 1_000))
        assertNull(firebaseClaimsUid(claims(exp = 500), "p1", 1_000))
        assertNull(firebaseClaimsUid(claims(sub = ""), "p1", 1_000))
    }

    @Test fun `base64url round trips without padding`() {
        val bytes = "hello?>~".encodeToByteArray()
        val e = base64UrlEncode(bytes)
        assertFalse('=' in e || '+' in e || '/' in e)
        assertEquals("hello?>~", base64UrlDecode(e).decodeToString())
    }
}
