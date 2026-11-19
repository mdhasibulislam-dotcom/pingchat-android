package com.mdhasibulislam.pingchat.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Group chats (v3.1.0, worker E).
 *
 * Storage (migration-v310-groups.sql — reuse, no new table):
 * - pingchat_conversations: is_group = true, group_name, group_avatar_url,
 *   created_by. Group text/photo/voice messages are ordinary
 *   pingchat_messages rows in that conversation (ChatRepository sends
 *   them unchanged — group calls are NOT built, calls stay 1-to-1).
 * - pingchat_conversation_participants: role 'admin' | 'member'. The
 *   creator is the first admin.
 *
 * All membership changes go through SECURITY DEFINER RPCs: the v3
 * participants RLS only lets a user insert/update their OWN row (and
 * has no DELETE policy), so an admin could never add/remove someone
 * else directly. The RPCs enforce admin-only + friend-only rules
 * server-side. Group photos reuse the public 'chat-photos' bucket.
 */
class GroupRepository {
    private val client get() = Supabase.client

    fun myId(): String? = client.auth.currentUserOrNull()?.id

    /** Create a group with [name] and [memberIds] (friends, not me). */
    suspend fun createGroup(
        name: String,
        memberIds: List<String>,
        photoBytes: ByteArray? = null,
        photoMime: String = "image/jpeg"
    ): String {
        val me = myId() ?: throw IllegalStateException("not signed in")
        var avatarUrl: String? = null
        if (photoBytes != null) {
            avatarUrl = uploadGroupPhoto(me, photoBytes, photoMime)
        }
        return client.postgrest.rpc(
            "pingchat_create_group",
            buildJsonObject {
                put("p_name", name.trim())
                put("p_member_ids", JsonArray(memberIds.map { JsonPrimitive(it) }))
                if (avatarUrl != null) put("p_avatar_url", avatarUrl)
            }
        ).decodeAs<String>()
    }

    /** Upload a group photo to the 'chat-photos' bucket; returns its public URL. */
    suspend fun uploadGroupPhoto(
        userId: String,
        bytes: ByteArray,
        mime: String
    ): String? {
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        return try {
            val path = "$userId/group-${UUID.randomUUID()}.$ext"
            client.storage.from("chat-photos").upload(path, bytes) {
                this.contentType = ContentType.parse(mime)
            }
            client.storage.from("chat-photos").publicUrl(path)
        } catch (e: Exception) {
            null
        }
    }

    /** Full group info: conversation + members with roles and profiles. */
    suspend fun loadGroupInfo(conversationId: String): GroupInfo? {
        val me = myId() ?: return null
        return try {
            val conv = client.from("pingchat_conversations").select {
                filter { eq("id", conversationId) }
            }.decodeSingleOrNull<Conversation>() ?: return null
            if (!conv.isGroup) return null
            val parts = client.from("pingchat_conversation_participants").select {
                filter { eq("conversation_id", conversationId) }
            }.decodeList<Participant>()
            val profiles = if (parts.isEmpty()) {
                emptyMap()
            } else {
                client.from("pingchat_profiles").select {
                    filter { isIn("id", parts.map { it.userId }) }
                }.decodeList<Profile>().associateBy { it.id }
            }
            val members = parts.map { GroupMember(it, profiles[it.userId]) }
            val myRole = parts.firstOrNull { it.userId == me }?.role ?: return null
            GroupInfo(conv, members, myRole, me)
        } catch (e: Exception) {
            null
        }
    }

    /** All participants (with profiles) of a group conversation. */
    suspend fun loadMembers(conversationId: String): List<GroupMember> {
        return loadGroupInfo(conversationId)?.members ?: emptyList()
    }

    /** Am I an admin of this group? */
    suspend fun isAdmin(conversationId: String): Boolean {
        return try {
            client.postgrest.rpc(
                "pingchat_is_group_admin",
                buildJsonObject { put("p_conversation_id", conversationId) }
            ).decodeAs<Boolean>()
        } catch (e: Exception) {
            false
        }
    }

    /** Add a friend to the group (admin only — enforced by the RPC). */
    suspend fun addMember(conversationId: String, userId: String) {
        client.postgrest.rpc(
            "pingchat_add_group_member",
            buildJsonObject {
                put("p_conversation_id", conversationId)
                put("p_user_id", userId)
            }
        )
    }

    /** Remove a member (admin only; never the last admin — RPC enforces). */
    suspend fun removeMember(conversationId: String, userId: String) {
        client.postgrest.rpc(
            "pingchat_remove_group_member",
            buildJsonObject {
                put("p_conversation_id", conversationId)
                put("p_user_id", userId)
            }
        )
    }

    /** Promote a member to admin (admin only). */
    suspend fun makeAdmin(conversationId: String, userId: String) {
        client.postgrest.rpc(
            "pingchat_make_group_admin",
            buildJsonObject {
                put("p_conversation_id", conversationId)
                put("p_user_id", userId)
            }
        )
    }

    /** Leave the group. If I was the last admin, the RPC promotes the
     *  longest-standing remaining member; if I was the last member, the
     *  conversation is deleted. */
    suspend fun leaveGroup(conversationId: String) {
        client.postgrest.rpc(
            "pingchat_leave_group",
            buildJsonObject { put("p_conversation_id", conversationId) }
        )
    }

    /** Rename the group and/or change its photo (admin only). */
    suspend fun updateGroup(
        conversationId: String,
        name: String? = null,
        photoBytes: ByteArray? = null,
        photoMime: String = "image/jpeg"
    ) {
        val me = myId() ?: throw IllegalStateException("not signed in")
        var avatarUrl: String? = null
        if (photoBytes != null) {
            avatarUrl = uploadGroupPhoto(me, photoBytes, photoMime)
        }
        client.postgrest.rpc(
            "pingchat_update_group",
            buildJsonObject {
                put("p_conversation_id", conversationId)
                if (name != null) put("p_name", name.trim())
                if (avatarUrl != null) put("p_avatar_url", avatarUrl)
            }
        )
    }

    /** Friends who are NOT yet members of this group (for the add-member picker). */
    suspend fun loadFriendsNotInGroup(conversationId: String): List<Profile> {
        val members = loadMembers(conversationId).map { it.participant.userId }.toSet()
        val friends = try {
            FriendsRepository().loadFriends()
        } catch (e: Exception) {
            emptyList()
        }
        return friends.filter { it.id !in members }
    }
}
