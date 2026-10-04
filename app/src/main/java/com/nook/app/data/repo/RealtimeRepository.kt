package com.nook.app.data.repo

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.nook.app.data.model.Presence
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/**
 * Presence, typing and the chat-membership mirror live in the Realtime Database (cheap,
 * onDisconnect-aware). We go offline whenever the app is backgrounded so we stay well under
 * the Spark plan's 100 simultaneous connections.
 *
 *  presence/{uid}            {online, lastSeen}
 *  typing/{chatId}/{uid}     server timestamp (removed on stop / disconnect)
 *  chatMembers/{chatId}/{uid} true  (mirror of Firestore members, used by RTDB rules)
 */
class RealtimeRepository(
    rtdbProvider: () -> FirebaseDatabase,
    private val auth: AuthRepository,
) {
    /** null when google-services.json has no database URL — presence/typing then quietly turn off. */
    private val db: FirebaseDatabase? by lazy {
        runCatching(rtdbProvider).onFailure { android.util.Log.w("Nook", "Realtime Database unavailable: ${it.message}") }.getOrNull()
    }
    private var connectedListener: ValueEventListener? = null
    private var serverOffset = 0L
    private val typingRefs = mutableSetOf<DatabaseReference>()

    fun goOnline() {
        val uid = auth.uid ?: return
        val rtdb = db ?: return
        rtdb.goOnline()
        if (connectedListener != null) return
        val me = rtdb.getReference("presence/$uid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) == true) {
                    me.onDisconnect().setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
                    me.setValue(mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP))
                }
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        rtdb.getReference(".info/connected").addValueEventListener(listener)
        connectedListener = listener
        rtdb.getReference(".info/serverTimeOffset").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { serverOffset = snapshot.getValue(Long::class.java) ?: 0L }
            override fun onCancelled(error: DatabaseError) = Unit
        })
    }

    /** Background / sign-out: mark offline explicitly, then drop the socket (onDisconnect is the safety net). */
    fun goOffline() {
        val rtdb = db ?: return
        connectedListener?.let { rtdb.getReference(".info/connected").removeEventListener(it) }
        connectedListener = null
        typingRefs.forEach { it.removeValue() }
        typingRefs.clear()
        auth.uid?.let { uid ->
            rtdb.getReference("presence/$uid")
                .setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
                .addOnCompleteListener { rtdb.goOffline() }
        } ?: rtdb.goOffline()
    }

    fun observePresence(uid: String): Flow<Presence> = callbackFlow {
        val rtdb = db ?: run { trySend(Presence()); awaitClose { }; return@callbackFlow }
        val ref = rtdb.getReference("presence/$uid")
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                trySend(Presence(online, lastSeen))
            }
            override fun onCancelled(error: DatabaseError) { trySend(Presence()) }
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }.distinctUntilChanged()

    /** Caller throttles (ChatViewModel writes at most every 3 s). */
    fun setTyping(chatId: String, typing: Boolean) {
        val uid = auth.uid ?: return
        val rtdb = db ?: return
        val ref = rtdb.getReference("typing/$chatId/$uid")
        if (typing) {
            ref.onDisconnect().removeValue()
            ref.setValue(ServerValue.TIMESTAMP)
            typingRefs += ref
        } else {
            ref.removeValue()
            typingRefs -= ref
        }
    }

    /** uids currently typing in [chatId], excluding me; stale entries (>8 s) are ignored. */
    fun observeTyping(chatId: String): Flow<Set<String>> = callbackFlow {
        val me = auth.uid
        val rtdb = db ?: run { trySend(emptySet()); awaitClose { }; return@callbackFlow }
        val ref = rtdb.getReference("typing/$chatId")
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis() + serverOffset
                val ids = snapshot.children.mapNotNull { c ->
                    val ts = c.getValue(Long::class.java) ?: return@mapNotNull null
                    c.key?.takeIf { it != me && now - ts < 8_000 }
                }.toSet()
                trySend(ids)
            }
            override fun onCancelled(error: DatabaseError) { trySend(emptySet()) }
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }.distinctUntilChanged()

    suspend fun addMembers(chatId: String, uids: Collection<String>) {
        if (uids.isEmpty()) return
        val rtdb = db ?: return
        rtdb.getReference("chatMembers/$chatId").updateChildren(uids.associateWith { true }).await()
    }

    suspend fun removeMember(chatId: String, uid: String) {
        val rtdb = db ?: return
        rtdb.getReference("chatMembers/$chatId/$uid").removeValue().await()
    }
}
