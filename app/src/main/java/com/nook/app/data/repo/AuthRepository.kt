package com.nook.app.data.repo

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.nook.app.AppConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class SignInCancelled : Exception("Sign-in cancelled")

/** Google sign-in only (Credential Manager + Google ID → Firebase Auth). */
class AuthRepository(private val auth: FirebaseAuth) {

    val currentUser: FirebaseUser? get() = auth.currentUser
    val uid: String? get() = auth.currentUser?.uid
    fun requireUid(): String = uid ?: error("Not signed in")

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** [activityContext] must be an Activity context so the Credential Manager UI can show. */
    suspend fun signInWithGoogle(activityContext: Context): FirebaseUser {
        val clientId = AppConfig.webClientId(activityContext)
        require(clientId.isNotBlank()) { "Google sign-in isn't configured. Add google-services.json (see README)." }
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request)
        } catch (e: GetCredentialCancellationException) {
            throw SignInCancelled()
        }
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            error("Unexpected credential type")
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
        return auth.signInWithCredential(firebaseCredential).await().user ?: error("Sign-in failed")
    }

    suspend fun idToken(forceRefresh: Boolean = false): String? =
        auth.currentUser?.getIdToken(forceRefresh)?.await()?.token

    suspend fun signOut(context: Context) {
        auth.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Re-authenticates with Google (needed before deleting the auth user) and deletes it. */
    suspend fun deleteAuthUser(activityContext: Context) {
        val user = auth.currentUser ?: return
        try {
            user.delete().await()
        } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
            val clientId = AppConfig.webClientId(activityContext)
            val option = GetSignInWithGoogleOption.Builder(clientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
            val google = GoogleIdTokenCredential.createFrom((result.credential as CustomCredential).data)
            user.reauthenticate(GoogleAuthProvider.getCredential(google.idToken, null)).await()
            user.delete().await()
        }
    }
}
