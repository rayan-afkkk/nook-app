package com.nook.app.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.nook.app.data.model.Call
import com.nook.app.data.model.CallStatus
import com.nook.app.data.model.CallType
import com.nook.app.data.model.Chat
import com.nook.app.data.model.ChatType
import com.nook.app.data.model.Disappearing
import com.nook.app.data.model.LastMessage
import com.nook.app.data.model.Media
import com.nook.app.data.model.Message
import com.nook.app.data.model.MessageType
import com.nook.app.data.model.PrivateSettings
import com.nook.app.data.model.ReplyRef
import com.nook.app.data.model.SendStatus
import com.nook.app.data.model.Sticker
import com.nook.app.data.model.StickerPack
import com.nook.app.data.model.User

/** Firestore wire format ↔ domain models. Keep field names in sync with firebase/firestore.rules. */
object Fields {
    const val USERS = "users"
    const val USERNAMES = "usernames"
    const val PRIVATE = "private"
    const val SETTINGS = "settings"
    const val TOKENS = "tokens"
    const val CHATS = "chats"
    const val MESSAGES = "messages"
    const val CALLS = "calls"
    const val STICKER_PACKS = "stickerPacks"
}

private val ESTIMATE = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE

private fun DocumentSnapshot.millis(field: String): Long? =
    getTimestamp(field, ESTIMATE)?.toDate()?.time

@Suppress("UNCHECKED_CAST")
private fun Any?.asMap(): Map<String, Any?>? = this as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
private fun Any?.asStringList(): List<String> = (this as? List<*>)?.filterIsInstance<String>() ?: emptyList()

private fun Any?.asMillis(): Long? = when (this) {
    is Timestamp -> toDate().time
    is Number -> toLong()
    else -> null
}

fun DocumentSnapshot.toUser(): User? {
    if (!exists()) return null
    return User(
        uid = id,
        username = getString("username").orEmpty(),
        displayName = getString("displayName").orEmpty(),
        photoUrl = getString("photoUrl"),
        createdAt = millis("createdAt") ?: 0,
    )
}

fun DocumentSnapshot.toPrivateSettings(): PrivateSettings {
    if (!exists()) return PrivateSettings()
    return PrivateSettings(
        blocked = get("blocked").asStringList(),
        mutedChats = get("mutedChats").asStringList(),
        notificationsEnabled = getBoolean("notificationsEnabled") ?: true,
        disappearingDefault = Disappearing.from(getString("disappearingDefault")),
    )
}

fun DocumentSnapshot.toChat(): Chat? {
    if (!exists()) return null
    val lm = get("lastMessage").asMap()
    val lastRead = get("lastRead").asMap().orEmpty().mapNotNull { (k, v) -> v.asMillis()?.let { k to it } }.toMap()
    return Chat(
        id = id,
        type = ChatType.from(getString("type")),
        members = get("members").asStringList(),
        name = getString("name"),
        photoUrl = getString("photoUrl"),
        createdBy = getString("createdBy").orEmpty(),
        createdAt = millis("createdAt") ?: 0,
        lastMessageAt = millis("lastMessageAt") ?: 0,
        lastMessage = lm?.let {
            LastMessage(
                id = it["id"] as? String ?: "",
                senderId = it["senderId"] as? String ?: "",
                type = MessageType.from(it["type"] as? String),
                preview = it["preview"] as? String ?: "",
            )
        },
        lastRead = lastRead,
        disappearing = Disappearing.from(getString("disappearing")),
        admins = get("admins").asStringList(),
    )
}

fun Map<String, Any?>.toMedia(): Media? {
    val url = this["url"] as? String ?: return null
    return Media(
        url = url,
        publicId = this["publicId"] as? String,
        resourceType = this["resourceType"] as? String,
        mime = this["mime"] as? String,
        name = this["name"] as? String,
        size = (this["size"] as? Number)?.toLong() ?: 0,
        width = (this["width"] as? Number)?.toInt() ?: 0,
        height = (this["height"] as? Number)?.toInt() ?: 0,
        durationMs = (this["durationMs"] as? Number)?.toLong() ?: 0,
        waveform = (this["waveform"] as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: emptyList(),
    )
}

fun Media.toMap(): Map<String, Any?> = buildMap {
    put("url", url)
    publicId?.let { put("publicId", it) }
    resourceType?.let { put("resourceType", it) }
    mime?.let { put("mime", it) }
    name?.let { put("name", it) }
    if (size > 0) put("size", size)
    if (width > 0) put("width", width)
    if (height > 0) put("height", height)
    if (durationMs > 0) put("durationMs", durationMs)
    if (waveform.isNotEmpty()) put("waveform", waveform)
}

fun DocumentSnapshot.toMessage(chatId: String): Message? {
    if (!exists()) return null
    val reply = get("replyTo").asMap()
    return Message(
        id = id,
        chatId = chatId,
        senderId = getString("senderId").orEmpty(),
        type = MessageType.from(getString("type")),
        text = getString("text").orEmpty(),
        media = get("media").asMap()?.toMedia(),
        replyTo = reply?.let {
            ReplyRef(
                messageId = it["messageId"] as? String ?: "",
                senderId = it["senderId"] as? String ?: "",
                type = MessageType.from(it["type"] as? String),
                preview = it["preview"] as? String ?: "",
            )
        },
        reactions = get("reactions").asMap().orEmpty().mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap(),
        createdAt = millis("createdAt") ?: (getLong("clientCreatedAt") ?: 0L),
        expireAt = millis("expireAt"),
        hiddenFor = get("hiddenFor").asStringList(),
        forwarded = getBoolean("forwarded") ?: false,
        status = if (metadata.hasPendingWrites()) SendStatus.SENDING else SendStatus.SENT,
    )
}

fun ReplyRef.toMap(): Map<String, Any?> = mapOf(
    "messageId" to messageId, "senderId" to senderId, "type" to type.wire, "preview" to preview,
)

fun DocumentSnapshot.toCall(): Call? {
    if (!exists()) return null
    return Call(
        id = id,
        chatId = getString("chatId").orEmpty(),
        callerId = getString("callerId").orEmpty(),
        calleeId = getString("calleeId").orEmpty(),
        type = CallType.from(getString("type")),
        status = CallStatus.from(getString("status")),
        createdAt = millis("createdAt") ?: 0,
        acceptedAt = millis("acceptedAt"),
        endedAt = millis("endedAt"),
    )
}

fun DocumentSnapshot.toStickerPack(): StickerPack? {
    if (!exists()) return null
    return StickerPack(
        id = id,
        chatId = getString("chatId").orEmpty(),
        name = getString("name").orEmpty(),
        createdBy = getString("createdBy").orEmpty(),
        members = get("members").asStringList(),
        stickers = (get("stickers") as? List<*>)?.mapNotNull { s ->
            val m = s.asMap() ?: return@mapNotNull null
            val url = m["url"] as? String ?: return@mapNotNull null
            Sticker(url, m["publicId"] as? String)
        } ?: emptyList(),
    )
}
