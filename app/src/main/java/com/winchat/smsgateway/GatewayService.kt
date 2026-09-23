package com.winchat.smsgateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class GatewayService : Service() {

    private var running = false
    private val pollUrl = "https://winnersonlineschool.com/winners/poll.php"
    private val authKey = "winners_gw_9f3a8c2e1b7d4f6a0c8e2d5b9a1f3c7e"
    private val pollIntervalMs = 20_000L
    private val channelId = "gateway_service"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            startForeground(1, buildNotification("Polling every 20s"))
            startPollingLoop()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun startPollingLoop() {
        thread {
            while (running) {
                try {
                    val response = httpGet(pollUrl)
                    Log.d("Gateway", "Poll response: $response")
                    processResponse(response)
                } catch (e: Exception) {
                    Log.e("Gateway", "Poll error: ${e.message}", e)
                }
                Thread.sleep(pollIntervalMs)
            }
        }
    }

    private fun httpGet(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("X-Gateway-Key", authKey)
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun processResponse(response: String) {
        if (response.isBlank()) return

        // Empty array = no jobs
        val trimmed = response.trim()
        if (trimmed == "[]") return

        try {
            val json = JSONObject(trimmed)
            val phone = json.optString("phone", "")
            val message = json.optString("message", "")
            if (phone.isEmpty() || message.isEmpty()) return

            Log.d("Gateway", "JOB: sending to $phone")
            val sender = SmsSender(this)
            val ok = sender.send(phone, message)
            Log.d("Gateway", if (ok) "SMS queued" else "SMS failed")
        } catch (e: Exception) {
            // Might be an array — skip
            Log.d("Gateway", "parse skip: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SMS Gateway Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("SMS Gateway")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
}
