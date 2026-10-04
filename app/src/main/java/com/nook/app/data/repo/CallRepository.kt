package com.nook.app.data.repo

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nook.app.data.firebase.Fields
import com.nook.app.data.firebase.snapshots
import com.nook.app.data.firebase.toCall
import com.nook.app.data.model.Call
import com.nook.app.data.model.CallStatus
import com.nook.app.data.model.CallType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** calls/{callId}: signalling + history. Media flows through LiveKit, never Firestore. */
class CallRepository(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
) {
    private val calls get() = db.collection(Fields.CALLS)

    suspend fun create(chatId: String, calleeId: String, type: CallType): String {
        val me = auth.requireUid()
        val ref = calls.document()
        ref.set(
            mapOf(
                "chatId" to chatId,
                "callerId" to me,
                "calleeId" to calleeId,
                "members" to listOf(me, calleeId),
                "type" to type.wire,
                "status" to CallStatus.RINGING.wire,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return ref.id
    }

    fun observe(callId: String): Flow<Call?> = calls.document(callId).snapshots().map { it.toCall() }

    suspend fun get(callId: String): Call? = calls.document(callId).get().await().toCall()

    suspend fun setStatus(callId: String, status: CallStatus) {
        val update = mutableMapOf<String, Any>("status" to status.wire)
        when (status) {
            CallStatus.ACCEPTED -> update["acceptedAt"] = FieldValue.serverTimestamp()
            CallStatus.ENDED, CallStatus.DECLINED, CallStatus.MISSED -> update["endedAt"] = FieldValue.serverTimestamp()
            CallStatus.RINGING -> Unit
        }
        calls.document(callId).update(update).await()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun history(): Flow<List<Call>> = auth.authState.flatMapLatest { u ->
        if (u == null) flowOf(emptyList<Call>())
        else calls.whereArrayContains("members", u.uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .snapshots()
            .map { s -> s.documents.mapNotNull { it.toCall() } }
    }
}
