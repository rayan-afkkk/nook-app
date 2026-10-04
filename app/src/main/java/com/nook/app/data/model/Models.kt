package com.nook.app.data.model

/**
 * Domain models. Firestore documents are mapped by hand in data/firebase/Mappers.kt
 * (no reflection), so field names here are free to differ from the wire format.
 */

enum class ChatType(val wire: String) {
    DIRECT("direct"), GROUP("group");
    companion object { fun from(w: String?) = entries.firstOrNull { it.wire == w } ?: DIRECT }
}

enum class MessageType(val wire: String) {
    TEXT("text"), IMAGE("image"), FILE("file"), VOICE("voice"), GIF("gif"), STICKER("sticker"), SYSTEM("system");
    companion object { fun from(w: String?) = entries.firstOrNull { it.wire == w } ?: TEXT }
}

enum class Disappearing(val wire: String, val millis: Long?, val label: String) {
    OFF("off", null, "Off"),
    DAY("24h", 24L * 60 * 60 * 1000, "24 hours"),
    WEEK("7d", 7L * 24 * 60 * 60 * 1000, "7 days");
    companion object { fun from(w: String?) = entries.firstOrNull { it.wire == w } ?: OFF }
}

data class User(
    val uid: String,
    val username: String,
    val displayName: String,
    val photoUrl: String?,
    val createdAt: Long = 0,
) {
    val name: String get() = displayName.ifBlank { username }
}

/** Owner-only settings at users/{uid}/private/settings (the Worker reads these too). */
data class PrivateSettings(
    val blocked: List<String> = emptyList(),
    val mutedChats: List<String> = emptyList(),
    val notificationsEnabled: Boolean = true,
    val disappearingDefault: Disappearing = Disappearing.OFF,
)

data class LastMessage(
    val id: String,
    val senderId: String,
    val type: MessageType,
    val preview: String,
)

data class Chat(
    val id: String,
    val type: ChatType,
    val members: List<String>,
    val name: String?,
    val photoUrl: String?,
    val createdBy: String,
    val createdAt: Long,
    val lastMessageAt: Long,
    val lastMessage: LastMessage?,
    val lastRead: Map<String, Long>,
    val disappearing: Disappearing,
    val admins: List<String> = emptyList(),
) {
    val isGroup: Boolean get() = type == ChatType.GROUP
    fun otherMember(me: String): String? = members.firstOrNull { it != me }
    fun isUnread(me: String): Boolean {
        val lm = lastMessage ?: return false
        if (lm.senderId == me) return false
        return lastMessageAt > (lastRead[me] ?: 0L)
    }
}

data class Media(
    val url: String,
    val publicId: String? = null,
    val resourceType: String? = null,
    val mime: String? = null,
    val name: String? = null,
    val size: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
    val waveform: List<Float> = emptyList(),
)

data class ReplyRef(
    val messageId: String,
    val senderId: String,
    val type: MessageType,
    val preview: String,
)

enum class SendStatus { SENDING, SENT, FAILED }

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val type: MessageType,
    val text: String = "",
    val media: Media? = null,
    val replyTo: ReplyRef? = null,
    val reactions: Map<String, String> = emptyMap(),
    val createdAt: Long = 0,
    val expireAt: Long? = null,
    val hiddenFor: List<String> = emptyList(),
    val forwarded: Boolean = false,
    val status: SendStatus = SendStatus.SENT,
    /** Local upload progress for outbox items, 0..1. */
    val progress: Float = 1f,
    /** Local content URI for messages still uploading. */
    val localUri: String? = null,
) {
    fun isExpired(now: Long): Boolean = expireAt != null && expireAt <= now

    fun previewText(): String = when (type) {
        MessageType.TEXT, MessageType.SYSTEM -> text
        MessageType.IMAGE -> if (text.isNotBlank()) "Photo · $text" else "Photo"
        MessageType.FILE -> media?.name ?: "File"
        MessageType.VOICE -> "Voice message"
        MessageType.GIF -> "GIF"
        MessageType.STICKER -> "Sticker"
    }
}

data class Presence(val online: Boolean = false, val lastSeen: Long = 0)

enum class CallType(val wire: String) {
    VOICE("voice"), VIDEO("video");
    companion object { fun from(w: String?) = entries.firstOrNull { it.wire == w } ?: VOICE }
}

enum class CallStatus(val wire: String) {
    RINGING("ringing"), ACCEPTED("accepted"), DECLINED("declined"), ENDED("ended"), MISSED("missed");
    companion object { fun from(w: String?) = entries.firstOrNull { it.wire == w } ?: ENDED }
}

data class Call(
    val id: String,
    val chatId: String,
    val callerId: String,
    val calleeId: String,
    val type: CallType,
    val status: CallStatus,
    val createdAt: Long,
    val acceptedAt: Long?,
    val endedAt: Long?,
) {
    fun peer(me: String) = if (callerId == me) calleeId else callerId
    fun isOutgoing(me: String) = callerId == me
    fun isMissedFor(me: String) = !isOutgoing(me) && (status == CallStatus.MISSED || status == CallStatus.DECLINED && acceptedAt == null)
    val durationSec: Long get() = if (acceptedAt != null && endedAt != null) (endedAt - acceptedAt) / 1000 else 0
}

data class Sticker(val url: String, val publicId: String? = null)

data class StickerPack(
    val id: String,
    val chatId: String,
    val name: String,
    val createdBy: String,
    val members: List<String>,
    val stickers: List<Sticker>,
)

enum class GiphyKind { GIFS, STICKERS }

data class GiphyItem(
    val id: String,
    val title: String,
    /** Small animated preview for grids. */
    val previewUrl: String,
    /** Full URL stored in the message. */
    val url: String,
    val width: Int,
    val height: Int,
)
