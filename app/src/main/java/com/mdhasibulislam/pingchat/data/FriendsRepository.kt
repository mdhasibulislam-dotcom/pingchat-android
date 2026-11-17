package com.mdhasibulislam.pingchat.data

import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Friend system: directory, requests, accept/decline — on Firestore (v2.0.0).
 *
 * Layout: friendships/{pairId} where pairId = the two uids sorted and joined
 * with "_": { requesterId, addresseeId, status: pending/accepted/declined,
 * createdAt (millis) }. One doc per pair, so a request can never duplicate.
 * Private-profile visibility is enforced client-side (public OR friend OR
 * pending-request preview, else name/photo only) — same as v1.0.5.
 */
class FriendsRepository {
    private val db get() = FirebaseDb.db

    fun myId(): String? = FirebaseDb.auth.currentUser?.uid

    private fun friendshipsRef() = db.collection("friendships")

    /** All users, for the People directory (avatar + name + username). */
    suspend fun loadAllUsers(): List<Profile> {
        return try {
            db.collection("users")
                .orderBy("fullName", Query.Direction.ASCENDING)
                .get()
                .await()
                .documents
                .map { profileFromDoc(it.id, it.data ?: emptyMap()) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Friendship state with one user, from the caller's point of view. */
    suspend fun friendshipStatus(userId: String): FriendState {
        val me = myId() ?: return FriendState.NONE
        return try {
            val snap = friendshipsRef().document(pairDocId(me, userId)).get().await()
            if (!snap.exists()) return FriendState.NONE
            val status = snap.getString("status") ?: return FriendState.NONE
            val requesterId = snap.getString("requesterId")
            when (status) {
                "accepted" -> FriendState.FRIENDS
                "declined" -> FriendState.DECLINED
                "pending" -> if (requesterId == me) FriendState.PENDING_OUT
                else FriendState.PENDING_IN
                else -> FriendState.NONE
            }
        } catch (e: Exception) {
            FriendState.NONE
        }
    }

    suspend fun sendFriendRequest(userId: String) {
        val me = myId() ?: return
        val now = nowMillis()
        friendshipsRef().document(pairDocId(me, userId)).set(
            mapOf(
                "requesterId" to me,
                "addresseeId" to userId,
                "status" to "pending",
                "createdAt" to now,
                "updatedAt" to now
            )
        ).await()
    }

    suspend fun respondFriendRequest(requesterId: String, accept: Boolean) {
        val me = myId() ?: return
        friendshipsRef().document(pairDocId(me, requesterId)).update(
            mapOf(
                "status" to if (accept) "accepted" else "declined",
                "updatedAt" to nowMillis()
            )
        ).await()
    }

    /** Cancel a pending request I sent. */
    suspend fun cancelFriendRequest(userId: String) {
        val me = myId() ?: return
        try {
            friendshipsRef().document(pairDocId(me, userId)).delete().await()
        } catch (e: Exception) {
            throw e
        }
    }

    /** Pending requests sent TO me, with the requester's profile. */
    suspend fun loadIncomingRequests(): List<Pair<Friendship, Profile>> {
        val me = myId() ?: return emptyList()
        return try {
            val rows = friendshipsRef()
                .whereEqualTo("addresseeId", me)
                .get()
                .await()
                .documents
                .map { friendshipFromDoc(it.id, it.data ?: emptyMap()) }
                .filter { it.status == "pending" }
                .sortedByDescending { it.createdAt }
            if (rows.isEmpty()) return emptyList()
            val profiles = rows.mapNotNull { row ->
                getProfile(row.requesterId)?.let { row to it }
            }
            profiles
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** My accepted friends (either side of the row), with their profiles. */
    suspend fun loadFriends(): List<Profile> {
        val me = myId() ?: return emptyList()
        return try {
            val asRequester = friendshipsRef()
                .whereEqualTo("requesterId", me)
                .get()
                .await()
                .documents
                .map { friendshipFromDoc(it.id, it.data ?: emptyMap()) }
            val asAddressee = friendshipsRef()
                .whereEqualTo("addresseeId", me)
                .get()
                .await()
                .documents
                .map { friendshipFromDoc(it.id, it.data ?: emptyMap()) }
            val ids = (asRequester + asAddressee)
                .filter { it.status == "accepted" }
                .map { if (it.requesterId == me) it.addresseeId else it.requesterId }
                .distinct()
            if (ids.isEmpty()) return emptyList()
            ids.mapNotNull { getProfile(it) }
                .sortedBy { it.fullName.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getProfile(userId: String): Profile? {
        return try {
            val snap = db.collection("users").document(userId).get().await()
            if (!snap.exists()) null else profileFromDoc(userId, snap.data ?: emptyMap())
        } catch (e: Exception) {
            null
        }
    }

    /** Live updates for the People badge: any change to my friendships. */
    fun friendshipsFlow(): Flow<Unit> = callbackFlow {
        val me = myId()
        if (me == null) {
            close()
            return@callbackFlow
        }
        val reg1 = friendshipsRef()
            .whereEqualTo("requesterId", me)
            .addSnapshotListener { _, _ -> trySend(Unit) }
        val reg2 = friendshipsRef()
            .whereEqualTo("addresseeId", me)
            .addSnapshotListener { _, _ -> trySend(Unit) }
        awaitClose {
            reg1.remove()
            reg2.remove()
        }
    }
}
