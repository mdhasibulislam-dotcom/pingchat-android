package com.mdhasibulislam.pingchat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Free push sending: the sender's phone calls our Supabase Edge Function
 * (free tier), which sends the FCM push with the Firebase service-account
 * key kept as a Supabase secret — the key never lives in the app, in
 * chat, or in git. FCM message delivery itself is free; no Blaze needed.
 */
object PushSender {
    private const val FUNCTION_URL =
        "https://tjsalnhtuqqigyhvgpnb.supabase.co/functions/v1/send-push"

    /** Supabase publishable (anon) key — public client config, safe to embed. */
    private const val ANON_KEY = "sb_publishable__lkX_8sN9yDzjoJFwd_A9A_2zOiaA0f"

    suspend fun sendPush(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("token", token)
                    put("title", title)
                    put("body", body)
                    put("data", JSONObject(data))
                }
                val conn = (URL(FUNCTION_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("apikey", ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $ANON_KEY")
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                    it.write(payload.toString())
                }
                val ok = conn.responseCode in 200..299
                conn.disconnect()
                ok
            } catch (e: Exception) {
                false
            }
        }
    }
}
