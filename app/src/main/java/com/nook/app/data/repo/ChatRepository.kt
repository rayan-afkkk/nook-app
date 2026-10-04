package com.nook.app.data.repo

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nook.app.data.firebase.Fields
import com.nook.app.data.firebase.snapshots
import com.nook.app.data.firebase.toChat
import com.nook.app.data.model.Chat
import com.nook.app.data.model.ChatType
import com.nook.app.data.model.Disappearing
import com.nook.app.data.model.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await

/**
 * chats/{chatId}. DMs use a deterministic id (dm_{uidA}_{uidB}, sorted) so two people can
 * never end up with duplicate conversations. One shared listener powers the whole chat list.
 */
class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
    private val realtime: RealtimeRepository,
    private val users: UserRepository,
    scope: CoroutineScope,
) {
    private val chats get() = db.collection(Fields.CHATS)

    /** null = loading. Shared so Chats + Friends tabs reuse ONE listener. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val chatList: Flow<List<Chat>?> = auth.authState.flatMapLatest { user ->
        if (user == null) flowOf(emptyList<Chat>())
        else chats.whereArrayContains("members", user.uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .limit(200)
            .snapshots()
            .map<com.google.firebase.firestore.QuerySnapshot, List<Chat>?> { snap -> snap.documents.mapNotNull { it.toChat() } }
            // A listener error (offline with no cache, missing index…) must never crash the shared scope.
            .catch { e -> android.util.Log.w("Nook", "chat list listener failed: ${e.message}"); emit(emptyList()) }
    }.shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    fun observeChat(chatId: String): Flow<Chat?> = chats.document(chatId).snapshots().map { it.toChat() }

    suspend fun getChat(chatId: String): Chat? = chats.document(chatId).get().await().toChat()

    private suspend fun defaultDisappearing(): Disappearing =
        runCatching { users.observePrivateSettings().first().disappearingDefault }.getOrDefault(Disappearing.OFF)

    /** Returns the DM chat id with [otherUid], creating the chat on first use. */
    suspend fun openDirect(otherUid: String): String {
        val me = auth.requireUid()
        require(otherUid != me) { "That's you!" }
        val id = dmId(me, otherUid)
        val ref = chats.document(id)
        val exists = runCatching { ref.get().await().exists() }.getOrDefault(false)
        if (!exists) {
            ref.set(
                mapOf(
                    "type" to ChatType.DIRECT.wire,
                    "members" to listOf(me, otherUid).sorted(),
                    "createdBy" to me,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastRead" to mapOf(me to FieldValue.serverTimestamp()),
                    "disappearing" to defaultDisappearing().wire,
                ),
            ).await()
            realtime.addMembers(id, listOf(me, otherUid))
        }
        return id
    }

    suspend fun createGroup(name: String, memberUids: List<String>): String {
        val me = auth.requireUid()
        val members = (memberUids + me).distinct()
        require(members.size >= 2) { "Add at least one friend" }
        val ref = chats.document()
        ref.set(
            mapOf(
                "type" to ChatType.GROUP.wire,
                "members" to members,
                "admins" to listOf(me),
                "name" to name.trim().take(40),
                "createdBy" to me,
                "createdAt" to FieldValue.serverTimestamp(),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastRead" to mapOf(me to FieldValue.serverTimestamp()),
                "disappearing" to defaultDisappearing().wire,
            ),
        ).await()
        realtime.addMembers(ref.id, members)
        systemMessage(ref.id, "created the group")
        return ref.id
    }

    suspend fun addMembers(chatId: String, uids: List<String>, names: List<String>) {
        if (uids.isEmpty()) return
        chats.document(chatId).update("members", FieldValue.arrayUnion(*uids.toTypedArray())).await()
        realtime.addMembers(chatId, uids)
        systemMessage(chatId, "added ${names.joinToString()}")
    }

    suspend fun leaveGroup(chatId: String) {
        val me = auth.requireUid()
        systemMessage(chatId, "left the group")
        chats.document(chatId).update(
            mapOf("members" to FieldValue.arrayRemove(me), "lastRead.$me" to FieldValue.delete(), "admins" to FieldValue.arrayRemove(me)),
        ).await()
        runCatching { realtime.removeMember(chatId, me) }
    }

    suspend fun renameGroup(chatId: String, name: String) {
        chats.document(chatId).update("name", name.trim().take(40)).await()
        systemMessage(chatId, "renamed the group to “${name.trim().take(40)}”")
    }

    suspend fun setGroupPhoto(chatId: String, url: String?) {
        chats.document(chatId).update("photoUrl", url).await()
    }

    suspend fun setDisappearing(chatId: String, value: Disappearing) {
        chats.document(chatId).update("disappearing", value.wire).await()
        systemMessage(
            chatId,
            if (value == Disappearing.OFF) "turned off disappearing messages" else "set messages to disappear after ${value.label}",
        )
    }

    /** One timestamp per member = read receipts without per-message writes. */
    suspend fun markRead(chatId: String) {
        val me = auth.uid ?: return
        runCatching { chats.document(chatId).update("lastRead.$me", FieldValue.serverTimestamp()).await() }
    }

    private suspend fun systemMessage(chatId: String, text: String) {
        val me = auth.requireUid()
        val msgRef = chats.document(chatId).collection(Fields.MESSAGES).document()
        val batch = db.batch()
        batch.set(
            msgRef,
            mapOf(
                "senderId" to me,
                "type" to MessageType.SYSTEM.wire,
                "text" to text,
                "reactions" to emptyMap<String, String>(),
                "hiddenFor" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "clientCreatedAt" to System.currentTimeMillis(),
                "expireAt" to null as Timestamp?,
                "forwarded" to false,
            ),
        )
        batch.commit().await()
    }

    companion object {
        fun dmId(a: String, b: String): String = "dm_" + listOf(a, b).sorted().joinToString("_")
    }
}
