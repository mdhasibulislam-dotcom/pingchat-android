package com.mdhasibulislam.pingchat.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Friend system: directory, requests, accept/decline — on Supabase (v3.0.0).
 *
 * Layout: pingchat_friendships { requester_id, addressee_id,
 * status: pending/accepted/declined, created_at, updated_at }. One row per
 * pair (unique index on the unordered pair), so a request can never
 * duplicate. Private-profile visibility is enforced client-side (public OR
 * friend OR pending-request preview, else name/photo only) — same as v2.0.9.
 */
class FriendsRepository {
    private val client get() = Supabase.client
    private val privacyRepo = PrivacyRepository()

    fun myId(): String? = client.auth.currentUserOrNull()?.id

    /**
     * May I message [userId] under their Messages setting
     * (Everybody / Friends)? ChatViewModel / worker E gate on this
     * before opening a chat or sending. Blocks count as "no".
     */
    suspend fun canMessageUser(userId: String): Boolean =
        try {
            privacyRepo.canMessageUser(userId)
        } catch (e: Exception) {
            true
        }

    /** All users, for the People directory (avatar + name + username). */
    suspend fun loadAllUsers(): List<Profile> {
        return try {
            client.from("pingchat_profiles").select {
                order("full_name", Order.ASCENDING)
            }.decodeList<Profile>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Friendship state with one user, from the caller's point of view. */
    suspend fun friendshipStatus(userId: String): FriendState {
        return try {
            val value = client.postgrest.rpc(
                "pingchat_friendship_status",
                buildJsonObject { put("p_user_id", userId) }
            ).decodeAs<String>()
            FriendState.fromRpc(value)
        } catch (e: Exception) {
            FriendState.NONE
        }
    }

    suspend fun sendFriendRequest(userId: String) {
        // v3.1.0 Privacy: no friend request to someone I blocked, or
        // from someone who blocked me — the server-side
        // pingchat_send_friend_request RPC does not check blocks.
        if (privacyRepo.isBlockedByMe(userId) || privacyRepo.amBlockedBy(userId)) {
            throw IllegalStateException("You cannot send a friend request to this person.")
        }
        // v3.2.4 (bug 2): enforce the RECIPIENT's "Who Can Add You"
        // setting — canSendFriendRequestTo had zero callers, so the
        // setting saved but never blocked anything. The message is
        // user-facing: it never exposes which setting blocked it.
        // (The recreated server RPC enforces the same rule as a
        // backstop; see database/migration-v324-privacy-fixes.sql.)
        if (!privacyRepo.canSendFriendRequestTo(userId)) {
            throw IllegalStateException("This person isn't accepting friend requests.")
        }
        client.postgrest.rpc(
            "pingchat_send_friend_request",
            buildJsonObject { put("p_user_id", userId) }
        )
    }

    /** One profile by id (for the friend-request push recipient token). */
    suspend fun loadProfile(userId: String): Profile? {
        return try {
            client.from("pingchat_profiles").select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun respondFriendRequest(requesterId: String, accept: Boolean) {
        client.postgrest.rpc(
            "pingchat_respond_friend_request",
            buildJsonObject {
                put("p_requester_id", requesterId)
                put("p_accept", accept)
            }
        )
    }

    /** Cancel a pending request I sent. */
    suspend fun cancelFriendRequest(userId: String) {
        val me = myId() ?: return
        try {
            client.from("pingchat_friendships").delete {
                filter {
                    eq("requester_id", me)
                    eq("addressee_id", userId)
                    eq("status", "pending")
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }

    /** Pending requests sent TO me, with the requester's profile. */
    suspend fun loadIncomingRequests(): List<Pair<Friendship, Profile>> {
        val me = myId() ?: return emptyList()
        return try {
            val rows = client.from("pingchat_friendships").select {
                filter {
                    eq("addressee_id", me)
                    eq("status", "pending")
                }
                order("created_at", Order.DESCENDING)
            }.decodeList<Friendship>()
            if (rows.isEmpty()) return emptyList()
            // v3.1.0 Privacy: requests from people I blocked stay
            // hidden — blocking someone must silence them everywhere.
            val blockedIds = try {
                privacyRepo.loadBlockedUserIds().toSet()
            } catch (e: Exception) {
                emptySet()
            }
            val visible = rows.filter { it.requesterId !in blockedIds }
            if (visible.isEmpty()) return emptyList()
            val profiles = client.from("pingchat_profiles").select {
                filter { isIn("id", visible.map { it.requesterId }) }
            }.decodeList<Profile>().associateBy { it.id }
            visible.mapNotNull { row ->
                val profile = profiles[row.requesterId] ?: return@mapNotNull null
                row to profile
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** My accepted friends (either side of the row), with their profiles. */
    suspend fun loadFriends(): List<Profile> {
        val me = myId() ?: return emptyList()
        return try {
            val asRequester = client.from("pingchat_friendships").select {
                filter {
                    eq("requester_id", me)
                    eq("status", "accepted")
                }
            }.decodeList<Friendship>()
            val asAddressee = client.from("pingchat_friendships").select {
                filter {
                    eq("addressee_id", me)
                    eq("status", "accepted")
                }
            }.decodeList<Friendship>()
            val ids = (asRequester.map { it.addresseeId } + asAddressee.map { it.requesterId }).distinct()
            if (ids.isEmpty()) return emptyList()
            client.from("pingchat_profiles").select {
                filter { isIn("id", ids) }
                order("full_name", Order.ASCENDING)
            }.decodeList<Profile>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Live updates for the People badge: any change to pingchat_friendships. */
    fun friendshipsFlow(): Flow<Unit> = callbackFlow {
        val scope = this
        val channel = try {
            RealtimeChannels.get("friendships-feed") {
                client.realtime.channel("friendships-feed")
            }.also {
                RealtimeChannels.ensureSubscribed("friendships-feed")
            }
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        val job = scope.launch {
            channel.postgresChangeFlow<PostgresAction>("public") {
                table = "pingchat_friendships"
            }.map { }.collect { trySend(Unit) }
        }
        awaitClose { job.cancel() }
    }
}
