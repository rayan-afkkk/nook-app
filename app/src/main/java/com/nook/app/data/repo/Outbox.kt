package com.nook.app.data.repo

import android.net.Uri
import com.nook.app.data.model.Chat
import com.nook.app.data.model.Media
import com.nook.app.data.model.Message
import com.nook.app.data.model.MessageType
import com.nook.app.data.model.ReplyRef
import com.nook.app.data.model.SendStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

sealed interface Payload {
    /** Already has everything it needs (text, GIF, sticker, forward). */
    data class Ready(val out: OutgoingMessage) : Payload
    data class Image(val uri: Uri, val caption: String, val replyTo: ReplyRef?) : Payload
    data class Document(val uri: Uri, val replyTo: ReplyRef?) : Payload
    data class Voice(val file: File, val durationMs: Long, val waveform: List<Float>, val replyTo: ReplyRef?) : Payload
}

private data class PendingItem(
    val id: String,
    val chat: Chat,
    val senderId: String,
    val payload: Payload,
    val status: SendStatus,
    val progress: Float,
    val createdAt: Long,
    val error: String? = null,
)

/**
 * App-scoped send queue: media uploads keep going if you leave the chat, failures stay visible
 * with a retry affordance. Text goes straight to Firestore (its offline cache is the optimistic
 * layer) and only lands here if the write is rejected.
 */
class Outbox(
    private val messages: MessageRepository,
    private val media: MediaRepository,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    private val items = MutableStateFlow<Map<String, PendingItem>>(emptyMap())

    fun pending(chatId: String): Flow<List<Message>> = items
        .map { m -> m.values.filter { it.chat.id == chatId }.sortedBy { it.createdAt }.map { it.toMessage() } }
        .distinctUntilChanged()

    fun send(chat: Chat, payload: Payload): String {
        val id = (payload as? Payload.Ready)?.out?.id ?: messages.newId(chat.id)
        val item = PendingItem(id, chat, auth.requireUid(), payload, SendStatus.SENDING, 0f, System.currentTimeMillis())
        process(item)
        return id
    }

    fun retry(id: String) {
        val item = items.value[id] ?: return
        process(item.copy(status = SendStatus.SENDING, progress = 0f, error = null))
    }

    fun discard(id: String) = items.update { it - id }

    private fun process(item: PendingItem) {
        when (val p = item.payload) {
            is Payload.Ready -> {
                items.update { it - item.id }
                messages.send(item.chat, p.out).addOnFailureListener { e ->
                    items.update { it + (item.id to item.copy(status = SendStatus.FAILED, error = e.message)) }
                }
            }
            else -> {
                items.update { it + (item.id to item) }
                scope.launch {
                    try {
                        val folder = media.chatFolder(item.chat.id)
                        val onProgress: (Float) -> Unit = { f -> updateProgress(item.id, f) }
                        val out = when (p) {
                            is Payload.Image -> OutgoingMessage(
                                item.id, MessageType.IMAGE, p.caption.trim(), media.uploadImage(p.uri, folder, onProgress), p.replyTo,
                            )
                            is Payload.Document -> OutgoingMessage(
                                item.id, MessageType.FILE, "", media.uploadFile(p.uri, folder, onProgress), p.replyTo,
                            )
                            is Payload.Voice -> OutgoingMessage(
                                item.id, MessageType.VOICE, "", media.uploadVoice(p.file, p.durationMs, p.waveform, folder, onProgress), p.replyTo,
                            )
                            is Payload.Ready -> p.out
                        }
                        // Firestore's local cache now shows the real message; drop our placeholder.
                        val task = messages.send(item.chat, out)
                        items.update { it - item.id }
                        if (p is Payload.Voice) runCatching { p.file.delete() }
                        task.await()
                    } catch (e: Exception) {
                        items.update { it + (item.id to item.copy(status = SendStatus.FAILED, error = e.message)) }
                    }
                }
            }
        }
    }

    private fun updateProgress(id: String, f: Float) {
        items.update { m -> m[id]?.let { m + (id to it.copy(progress = f)) } ?: m }
    }

    private fun PendingItem.toMessage(): Message {
        val (type, text, localUri, mediaPreview, reply) = when (val p = payload) {
            is Payload.Ready -> Quint(p.out.type, p.out.text, null, p.out.media, p.out.replyTo)
            is Payload.Image -> Quint(MessageType.IMAGE, p.caption, p.uri.toString(), null, p.replyTo)
            is Payload.Document -> Quint(MessageType.FILE, "", p.uri.toString(), media.describe(p.uri).let { Media(url = "", name = it.first, size = it.second) }, p.replyTo)
            is Payload.Voice -> Quint(MessageType.VOICE, "", p.file.absolutePath, Media(url = "", durationMs = p.durationMs, waveform = p.waveform), p.replyTo)
        }
        return Message(
            id = id, chatId = chat.id, senderId = senderId, type = type, text = text, media = mediaPreview,
            replyTo = reply, createdAt = createdAt, status = status, progress = progress, localUri = localUri,
        )
    }

    private data class Quint(val a: MessageType, val b: String, val c: String?, val d: Media?, val e: ReplyRef?)
}
