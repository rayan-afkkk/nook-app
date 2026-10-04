package com.nook.app.data.firebase

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Listener → Flow adapters. Collecting attaches the listener, cancelling detaches it, so
 * screens that stop collecting (lifecycle-aware) stop costing reads.
 */
fun DocumentReference.snapshots(includeMetadata: Boolean = false): Flow<DocumentSnapshot> = callbackFlow {
    val reg = addSnapshotListener(if (includeMetadata) MetadataChanges.INCLUDE else MetadataChanges.EXCLUDE) { snap, err ->
        if (err != null) close(err) else if (snap != null) trySend(snap)
    }
    awaitClose { reg.remove() }
}

fun Query.snapshots(includeMetadata: Boolean = false): Flow<QuerySnapshot> = callbackFlow {
    val reg = addSnapshotListener(if (includeMetadata) MetadataChanges.INCLUDE else MetadataChanges.EXCLUDE) { snap, err ->
        if (err != null) close(err) else if (snap != null) trySend(snap)
    }
    awaitClose { reg.remove() }
}
