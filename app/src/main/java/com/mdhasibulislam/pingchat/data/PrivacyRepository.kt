package com.mdhasibulislam.pingchat.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Who may see / contact me: Everybody, Friends only, or Nobody. */
enum class Visibility(val db: String, val label: String) {
    EVERYBODY("everybody", "Everybody"),
    FRIENDS("friends", "Friends"),
    NOBODY("nobody", "Nobody");

    companion object {
        fun fromDb(value: String?): Visibility =
            entries.firstOrNull { it.db == value } ?: EVERYBODY
    }
}

/** Who may message me: Everybody or Friends only (no Nobody — a
 *  stranger can always send a friend request first). */
enum class MessagesWho(val db: String, val label: String) {
    EVERYBODY("everybody", "Everybody"),
    FRIENDS("friends", "Friends");

    companion object {
        fun fromDb(value: String?): MessagesWho =
            entries.firstOrNull { it.db == value } ?: EVERYBODY
    }
}

/** Per-chat auto-delete timer (also the global default for new chats). */
enum class AutoDeleteTimer(val db: String, val label: String) {
    OFF("off", "Off"),
    HOURS_24("24h", "24 hours"),
    DAYS_7("7d", "7 days"),
    DAYS_30("30d", "30 days");

    companion object {
        fun fromDb(value: String?): AutoDeleteTimer =
            entries.firstOrNull { it.db == value } ?: OFF
    }
}

/** "Delete my account if away for …" (default 12 months). */
enum class InactiveDeleteAfter(val db: String, val label: String) {
    MONTHS_3("3mo", "3 months"),
    MONTHS_6("6mo", "6 months"),
    MONTHS_12("12mo", "12 months"),
    MONTHS_24("24mo", "24 months");

    companion object {
        fun fromDb(value: String?): InactiveDeleteAfter =
            entries.firstOrNull { it.db == value } ?: MONTHS_12
    }
}

/**
 * One row of pingchat_privacy_settings (see
 * database/migration-v310-privacy.sql). Stored as plain strings so an
 * unknown future value still decodes instead of crashing the app;
 * use the typed helpers (lastSeen, messages, calls, …) to read them.
 */
@Serializable
data class PrivacySettings(
    @SerialName("user_id") val userId: String = "",
    @SerialName("last_seen_visibility") val lastSeenVisibility: String = Visibility.EVERYBODY.db,
    @SerialName("messages_who") val messagesWho: String = MessagesWho.EVERYBODY.db,
    @SerialName("calls_who") val callsWho: String = Visibility.EVERYBODY.db,
    @SerialName("voice_who") val voiceWho: String = Visibility.EVERYBODY.db,
    @SerialName("forwarded_who") val forwardedWho: String = Visibility.EVERYBODY.db,
    @SerialName("bio_visibility") val bioVisibility: String = Visibility.EVERYBODY.db,
    @SerialName("auto_delete_default") val autoDeleteDefault: String = AutoDeleteTimer.OFF.db,
    @SerialName("inactive_delete_after") val inactiveDeleteAfter: String = InactiveDeleteAfter.MONTHS_12.db,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
) {
    val lastSeen: Visibility get() = Visibility.fromDb(lastSeenVisibility)
    val messages: MessagesWho get() = MessagesWho.fromDb(messagesWho)
    val calls: Visibility get() = Visibility.fromDb(callsWho)
    val voice: Visibility get() = Visibility.fromDb(voiceWho)
    val forwarded: Visibility get() = Visibility.fromDb(forwardedWho)
    val bio: Visibility get() = Visibility.fromDb(bioVisibility)
    val autoDelete: AutoDeleteTimer get() = AutoDeleteTimer.fromDb(autoDeleteDefault)
    val inactiveAfter: InactiveDeleteAfter get() = InactiveDeleteAfter.fromDb(inactiveDeleteAfter)
}

/** Only the six "who can …" settings of another user — what the
 *  pingchat_get_privacy_visibility RPC returns. */
data class PrivacyVisibility(
    val lastSeen: Visibility = Visibility.EVERYBODY,
    val messages: MessagesWho = MessagesWho.EVERYBODY,
    val calls: Visibility = Visibility.EVERYBODY,
    val voice: Visibility = Visibility.EVERYBODY,
    val forwarded: Visibility = Visibility.EVERYBODY,
    val bio: Visibility = Visibility.EVERYBODY
)

/** One row of pingchat_devices (see migration-v310-privacy.sql). */
@Serializable
data class DeviceInfo(
    @SerialName("id") val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("device_name") val deviceName: String = "",
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("revoked") val revoked: Boolean = false,
    @SerialName("created_at") val createdAt: String = ""
)

/** One row of pingchat_blocks (blocker_id, blocked_id). */
@Serializable
private data class BlockRow(
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String,
    @SerialName("created_at") val createdAt: String = ""
)

/**
 * Privacy & Security (v3.1.0, worker A): all 11 Telegram-style settings
 * live in pingchat_privacy_settings (+ pingchat_devices, pingchat_blocks,
 * and the per-chat pingchat_conversations.auto_delete column).
 *
 * The can* check functions are the enforcement surface for the other
 * workers: worker E (forward + message gating), worker D (voice
 * button), worker F (bio on profiles), CallManager (calls) and
 * SessionManager (presence) all call these — nobody re-implements
 * the rules. A missing settings row (migration not run yet, or a user
 * who never opened the screen) means the permissive defaults
 * (Everybody / Off / 12 months), matching the v3 behaviour.
 *
 * (Worker E's stub lived here first with block-only fallbacks for
 * canForwardMessage / canSeeLastSeen; this file now holds the real
 * per-user settings those two functions promised. Their signatures
 * are unchanged, so worker E's call sites keep working.)
 */
class PrivacyRepository {
    private val client get() = Supabase.client

    fun myId(): String? = client.auth.currentUserOrNull()?.id

    // ---------- my settings: read / write ----------

    /** My settings row, creating it with the defaults when missing. */
    suspend fun loadMySettings(): PrivacySettings {
        val me = myId() ?: return PrivacySettings()
        val existing = try {
            client.from("pingchat_privacy_settings").select {
                filter { eq("user_id", me) }
            }.decodeSingleOrNull<PrivacySettings>()
        } catch (e: Exception) {
            null
        }
        if (existing != null) return existing
        val defaults = PrivacySettings(userId = me)
        return try {
            client.from("pingchat_privacy_settings").insert(defaults) {
                select()
            }.decodeSingleOrNull<PrivacySettings>() ?: defaults
        } catch (e: Exception) {
            // Race with another sign-in, or the migration is not run yet.
            try {
                client.from("pingchat_privacy_settings").select {
                    filter { eq("user_id", me) }
                }.decodeSingleOrNull<PrivacySettings>()
            } catch (e2: Exception) {
                null
            } ?: defaults
        }
    }

    /** Save a whole settings row (upsert on user_id). */
    suspend fun saveSettings(settings: PrivacySettings) {
        val me = myId() ?: return
        client.from("pingchat_privacy_settings").upsert(
            settings.copy(userId = me)
        ) { onConflict = "user_id" }
    }

    /** Load, patch and save in one call; returns the saved row. */
    suspend fun updateMySettings(transform: (PrivacySettings) -> PrivacySettings): PrivacySettings {
        val updated = transform(loadMySettings())
        saveSettings(updated)
        return updated
    }

    suspend fun setLastSeenVisibility(value: Visibility): PrivacySettings =
        updateMySettings { it.copy(lastSeenVisibility = value.db) }

    suspend fun setMessagesWho(value: MessagesWho): PrivacySettings =
        updateMySettings { it.copy(messagesWho = value.db) }

    suspend fun setCallsWho(value: Visibility): PrivacySettings =
        updateMySettings { it.copy(callsWho = value.db) }

    suspend fun setVoiceWho(value: Visibility): PrivacySettings =
        updateMySettings { it.copy(voiceWho = value.db) }

    suspend fun setForwardedWho(value: Visibility): PrivacySettings =
        updateMySettings { it.copy(forwardedWho = value.db) }

    suspend fun setBioVisibility(value: Visibility): PrivacySettings =
        updateMySettings { it.copy(bioVisibility = value.db) }

    suspend fun setAutoDeleteDefault(value: AutoDeleteTimer): PrivacySettings =
        updateMySettings { it.copy(autoDeleteDefault = value.db) }

    suspend fun setInactiveDeleteAfter(value: InactiveDeleteAfter): PrivacySettings =
        updateMySettings { it.copy(inactiveDeleteAfter = value.db) }

    // ---------- another user's visibility settings ----------

    /**
     * The six "who can …" settings of [userId]. Tries the
     * pingchat_get_privacy_visibility RPC first; falls back to a
     * direct read (friends may read the row via RLS). Null only when
     * neither works — callers then use the permissive defaults.
     */
    suspend fun loadVisibilityOf(userId: String): PrivacyVisibility? {
        try {
            val json = client.postgrest.rpc(
                "pingchat_get_privacy_visibility",
                buildJsonObject { put("p_user_id", userId) }
            ).decodeAs<JsonObject?>()
            if (json != null) return visibilityFromJson(json)
        } catch (e: Exception) {
            // RPC missing (migration not run yet) — try the direct read.
        }
        return try {
            val row = client.from("pingchat_privacy_settings").select {
                filter { eq("user_id", userId) }
            }.decodeSingleOrNull<PrivacySettings>() ?: return null
            PrivacyVisibility(
                lastSeen = row.lastSeen,
                messages = row.messages,
                calls = row.calls,
                voice = row.voice,
                forwarded = row.forwarded,
                bio = row.bio
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun visibilityFromJson(json: JsonObject): PrivacyVisibility {
        fun str(key: String): String? =
            json[key]?.jsonPrimitive?.contentOrNull
        return PrivacyVisibility(
            lastSeen = Visibility.fromDb(str("last_seen_visibility")),
            messages = MessagesWho.fromDb(str("messages_who")),
            calls = Visibility.fromDb(str("calls_who")),
            voice = Visibility.fromDb(str("voice_who")),
            forwarded = Visibility.fromDb(str("forwarded_who")),
            bio = Visibility.fromDb(str("bio_visibility"))
        )
    }

    // ---------- friendship / blocks (shared by every check) ----------

    /** True when [userId] and I are accepted friends. */
    suspend fun isFriendWith(userId: String): Boolean {
        return try {
            val value = client.postgrest.rpc(
                "pingchat_friendship_status",
                buildJsonObject { put("p_user_id", userId) }
            ).decodeAs<String>()
            value == "friends"
        } catch (e: Exception) {
            false
        }
    }

    /** True when I blocked [userId]. */
    suspend fun isBlockedByMe(userId: String): Boolean {
        val me = myId() ?: return false
        return try {
            client.from("pingchat_blocks").select {
                filter {
                    eq("blocker_id", me)
                    eq("blocked_id", userId)
                }
            }.decodeList<BlockRow>().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /** True when [userId] blocked me. */
    suspend fun amBlockedBy(userId: String): Boolean {
        val me = myId() ?: return false
        return try {
            client.from("pingchat_blocks").select {
                filter {
                    eq("blocker_id", userId)
                    eq("blocked_id", me)
                }
            }.decodeList<BlockRow>().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /** True when either side blocked the other — no contact at all. */
    suspend fun isBlockedEitherWay(userId: String): Boolean =
        isBlockedByMe(userId) || amBlockedBy(userId)

    private fun Visibility.allows(isFriend: Boolean): Boolean = when (this) {
        Visibility.EVERYBODY -> true
        Visibility.FRIENDS -> isFriend
        Visibility.NOBODY -> false
    }

    // ---------- can* checks — the enforcement surface ----------

    /**
     * May I see [userId]'s last seen? (Worker E's call sites use this
     * name.) "Nobody" must also hide the "Online" line, not just the
     * timestamp; call sites show "Offline" then.
     */
    suspend fun canSeeLastSeen(userId: String): Boolean {
        val me = myId() ?: return false
        if (userId == me) return true
        if (isBlockedEitherWay(userId)) return false
        val visibility = loadVisibilityOf(userId) ?: return true
        return visibility.lastSeen.allows(isFriendWith(userId))
    }

    /** Alias of [canSeeLastSeen]. */
    suspend fun canSeeLastSeenOf(userId: String): Boolean = canSeeLastSeen(userId)

    /** May I see [userId]'s bio? (Worker F uses this on profiles.) */
    suspend fun canSeeBioOf(userId: String): Boolean {
        if (isBlockedEitherWay(userId)) return false
        val visibility = loadVisibilityOf(userId) ?: return true
        return visibility.bio.allows(isFriendWith(userId))
    }

    /** May I message [userId]? (Messages: Everybody / Friends.) */
    suspend fun canMessageUser(userId: String): Boolean {
        if (isBlockedEitherWay(userId)) return false
        val visibility = loadVisibilityOf(userId) ?: return true
        return when (visibility.messages) {
            MessagesWho.EVERYBODY -> true
            MessagesWho.FRIENDS -> isFriendWith(userId)
        }
    }

    /** Alias of [canMessageUser] for ChatViewModel / worker E. */
    suspend fun canSendMessageTo(userId: String): Boolean = canMessageUser(userId)

    /** May I call [userId]? The CALLEE's Calls setting governs. */
    suspend fun canCallUser(userId: String): Boolean {
        if (isBlockedEitherWay(userId)) return false
        val visibility = loadVisibilityOf(userId) ?: return true
        return visibility.calls.allows(isFriendWith(userId))
    }

    /** May [callerId] call ME? My own Calls setting governs (CallManager). */
    suspend fun canReceiveCallFrom(callerId: String): Boolean {
        if (isBlockedEitherWay(callerId)) return false
        val mine = try {
            loadMySettings()
        } catch (e: Exception) {
            return true
        }
        return mine.calls.allows(isFriendWith(callerId))
    }

    /** May I send a voice message to [userId]? (Worker D gates the button.) */
    suspend fun canSendVoiceMessageTo(userId: String): Boolean {
        if (isBlockedEitherWay(userId)) return false
        val visibility = loadVisibilityOf(userId) ?: return true
        return visibility.voice.allows(isFriendWith(userId))
    }

    /**
     * May I forward a message originally sent by [fromUserId]? The
     * ORIGINAL SENDER's Forwarded Messages setting governs (worker E).
     * A forwarded message always carries a "Forwarded" label, so the
     * recipient can see it was forwarded.
     */
    suspend fun canForwardMessage(fromUserId: String): Boolean {
        val me = myId() ?: return false
        if (fromUserId == me) return true
        if (isBlockedEitherWay(fromUserId)) return false
        val visibility = loadVisibilityOf(fromUserId) ?: return true
        return visibility.forwarded.allows(isFriendWith(fromUserId))
    }

    /** Alias of [canForwardMessage] for worker E. */
    suspend fun canForwardMessagesFrom(userId: String): Boolean = canForwardMessage(userId)

    // ---------- blocked users ----------

    /** Ids I blocked. */
    suspend fun loadBlockedUserIds(): List<String> {
        val me = myId() ?: return emptyList()
        return try {
            client.from("pingchat_blocks").select {
                filter { eq("blocker_id", me) }
            }.decodeList<BlockRow>().map { it.blockedId }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Profiles I blocked (for the Blocked Users list). */
    suspend fun loadBlockedUsers(): List<Profile> {
        val ids = loadBlockedUserIds()
        if (ids.isEmpty()) return emptyList()
        return try {
            client.from("pingchat_profiles").select {
                filter { isIn("id", ids) }
                order("full_name", Order.ASCENDING)
            }.decodeList<Profile>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun blockUser(userId: String) {
        val me = myId() ?: return
        client.from("pingchat_blocks").insert(
            buildJsonObject {
                put("blocker_id", me)
                put("blocked_id", userId)
            }
        )
    }

    suspend fun unblockUser(userId: String) {
        val me = myId() ?: return
        client.from("pingchat_blocks").delete {
            filter {
                eq("blocker_id", me)
                eq("blocked_id", userId)
            }
        }
    }

    // ---------- devices ----------

    companion object {
        /** Stable per-device id for this app (ANDROID_ID). */
        fun deviceId(context: Context): String =
            try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: "unknown"
            } catch (e: Exception) {
                "unknown"
            }

        /** "Samsung SM-A546E" style display name. */
        fun deviceDisplayName(): String =
            "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "This phone" }
    }

    /**
     * Record this device on sign-in (upsert on user_id + device_id).
     * Returns false when this device was remotely logged out
     * (revoked = true) — the caller must sign out instead of
     * un-revoking it. True otherwise (including offline failures).
     */
    suspend fun registerCurrentDevice(context: Context?): Boolean {
        val ctx = context ?: return true
        val me = myId() ?: return true
        val id = deviceId(ctx)
        val existing = try {
            client.from("pingchat_devices").select {
                filter {
                    eq("user_id", me)
                    eq("device_id", id)
                }
            }.decodeSingleOrNull<DeviceInfo>()
        } catch (e: Exception) {
            null
        }
        if (existing?.revoked == true) return false
        return try {
            client.from("pingchat_devices").upsert(
                DeviceInfo(
                    userId = me,
                    deviceId = id,
                    deviceName = deviceDisplayName(),
                    lastSeen = nowIso(),
                    revoked = false
                )
            ) { onConflict = "user_id,device_id" }
            true
        } catch (e: Exception) {
            true // best-effort: a device row must never block sign-in
        }
    }

    /** Has THIS device been remotely logged out (revoked = true)? */
    suspend fun isCurrentDeviceRevoked(context: Context?): Boolean {
        val ctx = context ?: return false
        val me = myId() ?: return false
        return try {
            val row = client.from("pingchat_devices").select {
                filter {
                    eq("user_id", me)
                    eq("device_id", deviceId(ctx))
                }
            }.decodeSingleOrNull<DeviceInfo>()
            row?.revoked == true
        } catch (e: Exception) {
            false
        }
    }

    /** My signed-in devices, most recently seen first. */
    suspend fun loadDevices(): List<DeviceInfo> {
        val me = myId() ?: return emptyList()
        return try {
            client.from("pingchat_devices").select {
                filter { eq("user_id", me) }
                order("last_seen", Order.DESCENDING)
            }.decodeList<DeviceInfo>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Log out another device of mine remotely: flag it revoked, so
     * it signs itself out on its next start / foreground check.
     * (A client cannot revoke another device's JWT directly.)
     */
    suspend fun logoutDevice(deviceId: String) {
        val me = myId() ?: return
        client.from("pingchat_devices").update({
            set("revoked", true)
        }) {
            filter {
                eq("user_id", me)
                eq("device_id", deviceId)
            }
        }
    }

    /** Forget a device row entirely (e.g. a phone I no longer own). */
    suspend fun removeDevice(deviceId: String) {
        val me = myId() ?: return
        client.from("pingchat_devices").delete {
            filter {
                eq("user_id", me)
                eq("device_id", deviceId)
            }
        }
    }

    // ---------- per-chat auto-delete timer ----------

    /** This conversation's timer (Off when unset / migration not run). */
    suspend fun getConversationAutoDelete(conversationId: String): AutoDeleteTimer {
        return try {
            val row = client.from("pingchat_conversations").select {
                filter { eq("id", conversationId) }
            }.decodeSingleOrNull<JsonObject>()
            AutoDeleteTimer.fromDb(row?.get("auto_delete")?.jsonPrimitive?.contentOrNull)
        } catch (e: Exception) {
            AutoDeleteTimer.OFF
        }
    }

    /** Change a conversation's timer (from chat settings, worker E's UI). */
    suspend fun setConversationAutoDelete(conversationId: String, timer: AutoDeleteTimer) {
        client.from("pingchat_conversations").update({
            set("auto_delete", timer.db)
        }) {
            filter { eq("id", conversationId) }
        }
    }
}
