package com.nook.app.data.repo

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.nook.app.data.firebase.Fields
import com.nook.app.data.firebase.snapshots
import com.nook.app.data.firebase.toPrivateSettings
import com.nook.app.data.firebase.toUser
import com.nook.app.data.model.Disappearing
import com.nook.app.data.model.PrivateSettings
import com.nook.app.data.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

class UsernameTakenException : Exception("That username is taken")

/**
 * Profiles live at users/{uid}; the username claim at usernames/{username}.
 * Avatars are ALWAYS read from the user doc via [observeUser] (one shared listener per uid),
 * never copied into messages, so a photo change shows up everywhere immediately.
 */
class UserRepository(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    private val users get() = db.collection(Fields.USERS)
    private val userFlows = ConcurrentHashMap<String, Flow<User?>>()

    fun observeUser(uid: String): Flow<User?> = userFlows.getOrPut(uid) {
        users.document(uid).snapshots()
            .map { it.toUser() }
            .catch { emit(null) }
            .distinctUntilChanged()
            .shareIn(scope, SharingStarted.WhileSubscribed(10_000), replay = 1)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMe(): Flow<User?> = auth.authState.flatMapLatest { u -> if (u == null) flowOf(null) else observeUser(u.uid) }

    suspend fun getUser(uid: String): User? = users.document(uid).get().await().toUser()

    suspend fun profileExists(uid: String): Boolean = runCatching { users.document(uid).get().await().exists() }.getOrDefault(false)

    suspend fun findByUsername(raw: String): User? {
        val name = UsernameRules.normalize(raw)
        if (UsernameRules.validate(name) != null) return null
        val claim = db.collection(Fields.USERNAMES).document(name).get().await()
        val uid = claim.getString("uid") ?: return null
        return getUser(uid)
    }

    suspend fun isUsernameAvailable(name: String): Boolean =
        !db.collection(Fields.USERNAMES).document(name).get().await().exists()

    /** Claims the username and creates the profile atomically (rules enforce uniqueness). */
    suspend fun createProfile(username: String, displayName: String, photoUrl: String?) {
        val uid = auth.requireUid()
        val claimRef = db.collection(Fields.USERNAMES).document(username)
        try {
            db.runTransaction<Unit> { tx ->
                if (tx.get(claimRef).exists()) throw UsernameTakenException()
                tx.set(claimRef, mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp()))
                tx.set(
                    users.document(uid),
                    mapOf(
                        "uid" to uid,
                        "username" to username,
                        "displayName" to displayName.trim().take(40),
                        "photoUrl" to photoUrl,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                Unit
            }.await()
        } catch (e: Exception) {
            if (e is UsernameTakenException || e.cause is UsernameTakenException) throw UsernameTakenException()
            throw e
        }
        users.document(uid).collection(Fields.PRIVATE).document(Fields.SETTINGS)
            .set(mapOf("blocked" to emptyList<String>(), "mutedChats" to emptyList<String>(), "notificationsEnabled" to true), SetOptions.merge())
    }

    suspend fun updateDisplayName(name: String) {
        users.document(auth.requireUid()).update("displayName", name.trim().take(40)).await()
    }

    suspend fun updatePhoto(url: String?, publicId: String?) {
        users.document(auth.requireUid()).update(mapOf("photoUrl" to url, "photoPublicId" to publicId)).await()
    }

    suspend fun currentPhotoPublicId(): String? =
        users.document(auth.requireUid()).get().await().getString("photoPublicId")

    // ---- private settings ----

    private fun settingsRef(uid: String = auth.requireUid()) =
        users.document(uid).collection(Fields.PRIVATE).document(Fields.SETTINGS)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePrivateSettings(): Flow<PrivateSettings> = auth.authState.flatMapLatest { u ->
        if (u == null) flowOf(PrivateSettings())
        else settingsRef(u.uid).snapshots().map { it.toPrivateSettings() }.catch { emit(PrivateSettings()) }
    }

    suspend fun block(uid: String) { settingsRef().set(mapOf("blocked" to FieldValue.arrayUnion(uid)), SetOptions.merge()).await() }
    suspend fun unblock(uid: String) { settingsRef().set(mapOf("blocked" to FieldValue.arrayRemove(uid)), SetOptions.merge()).await() }

    suspend fun setMuted(chatId: String, muted: Boolean) {
        val op = if (muted) FieldValue.arrayUnion(chatId) else FieldValue.arrayRemove(chatId)
        settingsRef().set(mapOf("mutedChats" to op), SetOptions.merge()).await()
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        settingsRef().set(mapOf("notificationsEnabled" to enabled), SetOptions.merge()).await()
    }

    suspend fun setDisappearingDefault(d: Disappearing) {
        settingsRef().set(mapOf("disappearingDefault" to d.wire), SetOptions.merge()).await()
    }

    // ---- FCM tokens (users/{uid}/tokens/{token}) ----

    suspend fun saveFcmToken(token: String) {
        val uid = auth.uid ?: return
        users.document(uid).collection(Fields.TOKENS).document(token)
            .set(mapOf("token" to token, "platform" to "android", "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun removeFcmToken(token: String) {
        val uid = auth.uid ?: return
        runCatching { users.document(uid).collection(Fields.TOKENS).document(token).delete().await() }
    }

    /** Deletes profile, username claim, private settings and every device token. */
    suspend fun deleteAccountData() {
        val uid = auth.requireUid()
        val me = users.document(uid).get().await()
        val username = me.getString("username")
        val tokens = users.document(uid).collection(Fields.TOKENS).get().await()
        val batch = db.batch()
        tokens.documents.forEach { batch.delete(it.reference) }
        batch.delete(settingsRef(uid))
        if (username != null) batch.delete(db.collection(Fields.USERNAMES).document(username))
        batch.delete(users.document(uid))
        batch.commit().await()
    }
}
