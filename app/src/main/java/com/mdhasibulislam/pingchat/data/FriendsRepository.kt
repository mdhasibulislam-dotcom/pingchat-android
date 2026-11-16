package com.mdhasibulislam.pingchat.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Friend system: directory, requests, accept/decline. Mirrors js/friends.js + js/people.js. */
class FriendsRepository {
    private val client get() = Supabase.client

    fun myId(): String? = client.auth.currentUserOrNull()?.id

    /** All users, for the People directory (avatar + name + username). */
    suspend fun loadAllUsers(): List<Profile> {
        return client.from("profiles").select {
            order("full_name", Order.ASCENDING)
        }.decodeList<Profile>()
    }

    /** Friendship state with one user, from the caller's point of view. */
    suspend fun friendshipStatus(userId: String): FriendState {
        return try {
            val value = client.postgrest.rpc(
                "friendship_status",
                buildJsonObject { put("p_user_id", userId) }
            ).decodeAs<String>()
            FriendState.fromRpc(value)
        } catch (e: Exception) {
            FriendState.NONE
        }
    }

    suspend fun sendFriendRequest(userId: String) {
        client.postgrest.rpc(
            "send_friend_request",
            buildJsonObject { put("p_user_id", userId) }
        )
    }

    suspend fun respondFriendRequest(requesterId: String, accept: Boolean) {
        client.postgrest.rpc(
            "respond_friend_request",
            buildJsonObject {
                put("p_requester_id", requesterId)
                put("p_accept", accept)
            }
        )
    }

    /** Cancel a pending request I sent. */
    suspend fun cancelFriendRequest(userId: String) {
        val me = myId() ?: return
        client.from("friendships").delete {
            filter {
                eq("requester_id", me)
                eq("addressee_id", userId)
                eq("status", "pending")
            }
        }
    }

    /** Pending requests sent TO me, with the requester's profile. */
    suspend fun loadIncomingRequests(): List<Pair<Friendship, Profile>> {
        val me = myId() ?: return emptyList()
        val rows = client.from("friendships").select {
            filter {
                eq("addressee_id", me)
                eq("status", "pending")
            }
            order("created_at", Order.DESCENDING)
        }.decodeList<Friendship>()
        if (rows.isEmpty()) return emptyList()
        val profiles = client.from("profiles").select {
            filter { isIn("id", rows.map { it.requesterId }) }
        }.decodeList<Profile>().associateBy { it.id }
        return rows.mapNotNull { row ->
            val profile = profiles[row.requesterId] ?: return@mapNotNull null
            row to profile
        }
    }

    /** My accepted friends (either side of the row), with their profiles. */
    suspend fun loadFriends(): List<Profile> {
        val me = myId() ?: return emptyList()
        val asRequester = client.from("friendships").select {
            filter {
                eq("requester_id", me)
                eq("status", "accepted")
            }
        }.decodeList<Friendship>()
        val asAddressee = client.from("friendships").select {
            filter {
                eq("addressee_id", me)
                eq("status", "accepted")
            }
        }.decodeList<Friendship>()
        val ids = (asRequester.map { it.addresseeId } + asAddressee.map { it.requesterId }).distinct()
        if (ids.isEmpty()) return emptyList()
        return client.from("profiles").select {
            filter { isIn("id", ids) }
            order("full_name", Order.ASCENDING)
        }.decodeList<Profile>()
    }

    /** Live updates for the People badge: INSERT / UPDATE / DELETE on friendships. */
    fun friendshipsFeedChannel() = client.realtime.channel("friendships-feed")

    fun friendshipsChangeFlow(channel: io.github.jan.supabase.realtime.RealtimeChannel): Flow<Unit> =
        channel.postgresChangeFlow<PostgresAction>("public") {
            table = "friendships"
        }.map { }
}
