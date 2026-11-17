package com.mdhasibulislam.pingchat.data

import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await

/** My profile: view, edit, photo upload — on Firestore + Firebase Storage. */
class ProfileRepository {
    private val db get() = FirebaseDb.db
    private val storage get() = FirebaseDb.storage

    fun myId(): String? = FirebaseDb.auth.currentUser?.uid

    suspend fun getProfile(userId: String): Profile? {
        return try {
            val snap = db.collection("users").document(userId).get().await()
            if (!snap.exists()) null else profileFromDoc(userId, snap.data ?: emptyMap())
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateProfile(userId: String, fullName: String, bio: String?, phone: String?, isPublic: Boolean): Profile? {
        return try {
            db.collection("users").document(userId).update(
                mapOf(
                    "fullName" to fullName,
                    "bio" to bio,
                    "phone" to phone,
                    "isPublic" to isPublic,
                    "updatedAt" to nowMillis()
                )
            ).await()
            getProfile(userId)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Upload a profile photo to Firebase Storage at
     * avatars/<userId>/<timestamp>.<ext> and return its download URL.
     * Returns null on failure.
     */
    suspend fun uploadAvatar(userId: String, bytes: ByteArray, ext: String, contentType: String): String? {
        return try {
            val path = "avatars/$userId/${System.currentTimeMillis()}.$ext"
            val ref = storage.reference.child(path)
            ref.putBytes(
                bytes,
                StorageMetadata.Builder().setContentType(contentType).build()
            ).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun setAvatarUrl(userId: String, url: String): Profile? {
        return try {
            db.collection("users").document(userId).update(
                mapOf(
                    "avatarUrl" to url,
                    "updatedAt" to nowMillis()
                )
            ).await()
            getProfile(userId)
        } catch (e: Exception) {
            null
        }
    }
}
