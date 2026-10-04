package com.nook.app.data.repo

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nook.app.AppConfig
import com.nook.app.data.firebase.Fields
import com.nook.app.data.firebase.snapshots
import com.nook.app.data.firebase.toMap
import com.nook.app.data.firebase.toMessage
import com.nook.app.data.model.Chat
import com.nook.app.data.model.Media
import com.nook.app.data.model.Message
import com.nook.app.data.model.MessageType
import com.nook.app.data.model.ReplyRef
import com.nook.app.data.remote.MediaAsset
import com.nook.app.data.remote.WorkerApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

/** What the composer hands to the data layer. */
data class OutgoingMessage(
    val id: String,
    val type: MessageType,
    val text: String = "",
    val media: Media? = null,
    val replyTo: ReplyRef? = null,
    val forwarded: Boolean = false,
)

data class LiveWindow(val messages: List<Message>, val fromCache: Boolean)

/**
 * chats/{chatId}/messages/{id}. Live listener on the latest [AppConfig.PAGE_SIZE]; older pages
 * are one-shot reads. Each send is ONE batch: the message + the chat's lastMessage/lastRead.
 */
class MessageRepository(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
    private val worker: WorkerApi,
    private val appScope: CoroutineScope,
) {
    private fun chatRef(chatId: String) = db.collection(Fields.CHATS).document(chatId)
    private fun messages(chatId: String) = chatRef(chatId).collection(Fields.MESSAGES)

    fun newId(chatId: String): String = messages(chatId).document().id

    fun observeLatest(chatId: String, limit: Long = AppConfig.PAGE_SIZE): Flow<LiveWindow> =
        messages(chatId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .snapshots(includeMetadata = true)
            .map { snap ->
                LiveWindow(snap.documents.mapNotNull { it.toMessage(chatId) }, snap.metadata.isFromCache)
            }

    suspend fun loadOlder(chatId: String, beforeCreatedAt: Long, limit: Long = AppConfig.PAGE_SIZE): List<Message> =
        messages(chatId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(Timestamp(Date(beforeCreatedAt)))
            .limit(limit)
            .get().await()
            .documents.mapNotNull { it.toMessage(chatId) }

    /**
     * Writes optimistically (Firestore's offline cache shows it instantly, even offline) and
     * returns the commit task. After the server commit we ping the Worker for push (fire & forget).
     */
    fun send(chat: Chat, out: OutgoingMessage): Task<Void> {
        val me = auth.requireUid()
        val now = System.currentTimeMillis()
        val expireAt = chat.disappearing.millis?.let { Timestamp(Date(now + it)) }
        val data = buildMap<String, Any?> {
            put("senderId", me)
            put("type", out.type.wire)
            put("text", out.text)
            out.media?.let { put("media", it.toMap()) }
            out.replyTo?.let { put("replyTo", it.toMap()) }
            put("reactions", emptyMap<String, String>())
            put("hiddenFor", emptyList<String>())
            put("createdAt", FieldValue.serverTimestamp())
            put("clientCreatedAt", now)
            put("expireAt", expireAt)
            put("forwarded", out.forwarded)
        }
        val preview = previewFor(out)
        val batch = db.batch()
        batch.set(messages(chat.id).document(out.id), data)
        batch.update(
            chatRef(chat.id),
            mapOf(
                "lastMessage" to mapOf("id" to out.id, "senderId" to me, "type" to out.type.wire, "preview" to preview),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastRead.$me" to FieldValue.serverTimestamp(),
            ),
        )
        val task = batch.commit()
        appScope.launch {
            runCatching {
                task.await()
                if (AppConfig.hasWorker) worker.notifyMessage(chat.id)
            }
        }
        return task
    }

    suspend fun react(chatId: String, messageId: String, emoji: String?) {
        val me = auth.requireUid()
        messages(chatId).document(messageId)
            .update("reactions.$me", emoji ?: FieldValue.delete()).await()
    }

    suspend fun hideForMe(chatId: String, messageId: String) {
        messages(chatId).document(messageId).update("hiddenFor", FieldValue.arrayUnion(auth.requireUid())).await()
    }

    /** Removes the message for everyone (sender only) and deletes its Cloudinary asset. */
    suspend fun unsend(chat: Chat, message: Message) {
        messages(chat.id).document(message.id).delete().await()
        if (chat.lastMessage?.id == message.id) {
            runCatching {
                chatRef(chat.id).update(
                    "lastMessage",
                    mapOf("id" to message.id, "senderId" to message.senderId, "type" to MessageType.SYSTEM.wire, "preview" to "Message unsent"),
                ).await()
            }
        }
        message.media?.assetOrNull()?.let { asset ->
            appScope.launch { runCatching { worker.deleteMedia(chat.id, listOf(asset)) } }
        }
    }

    /** Any member's app deletes a small batch of expired messages when the chat opens. */
    suspend fun deleteExpired(chatId: String, max: Long = 25) {
        val expired = messages(chatId).whereLessThan("expireAt", Timestamp.now()).limit(max).get().await()
        if (expired.isEmpty) return
        val batch = db.batch()
        val assets = mutableListOf<MediaAsset>()
        expired.documents.forEach { d ->
            batch.delete(d.reference)
            d.toMessage(chatId)?.media?.assetOrNull()?.let(assets::add)
        }
        batch.commit().await()
        if (assets.isNotEmpty() && AppConfig.hasWorker) runCatching { worker.deleteMedia(chatId, assets) }
    }

    /** Account deletion: every message I sent, in every chat, plus its media. */
    suspend fun deleteAllMine() {
        val me = auth.requireUid()
        while (true) {
            val page = db.collectionGroup(Fields.MESSAGES).whereEqualTo("senderId", me).limit(200).get().await()
            if (page.isEmpty) break
            val batch = db.batch()
            val assetsByChat = mutableMapOf<String, MutableList<MediaAsset>>()
            page.documents.forEach { d ->
                batch.delete(d.reference)
                val chatId = d.reference.parent.parent?.id ?: return@forEach
                d.toMessage(chatId)?.media?.assetOrNull()?.let { assetsByChat.getOrPut(chatId) { mutableListOf() } += it }
            }
            batch.commit().await()
            if (AppConfig.hasWorker) assetsByChat.forEach { (chatId, assets) -> runCatching { worker.deleteMedia(chatId, assets) } }
            if (page.size() < 200) break
        }
    }

    companion object {
        fun previewFor(out: OutgoingMessage): String = when (out.type) {
            MessageType.TEXT, MessageType.SYSTEM -> out.text.take(120)
            MessageType.IMAGE -> "Photo"
            MessageType.FILE -> out.media?.name ?: "File"
            MessageType.VOICE -> "Voice message"
            MessageType.GIF -> "GIF"
            MessageType.STICKER -> "Sticker"
        }

        fun Media.assetOrNull(): MediaAsset? {
            val id = publicId ?: return null
            return MediaAsset(id, resourceType ?: "image")
        }
    }
}
