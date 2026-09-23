package com.winchat.smsgateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class GatewayService : Service() {

    private var running = false
    private val pollUrl = "https://winnersonlineschool.com/winners/poll.php"
    private val authKey = "winners_gw_9f3a8c2e1b7d4f6a0c8e2d5b9a1f3c7e"
    private val pollIntervalMs = 20_000L
    private val channelId = "gateway_channel"
    private val notifId = 1001

    override fun onCreate() {
        super.onCreate()
        Log.d("Gateway", "onCreate")
        try {
            createNotificationChannel()
            val notif = buildNotification("Starting...")
            startForeground(notifId, notif)
            Log.d("Gateway", "startForeground OK")
        } catch (e: Exception) {
            Log.e("Gateway", "startForeground failed", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("Gateway", "onStartCommand")
        if (!running) {
            running = true
            startPollingLoop()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d("Gateway", "onDestroy")
        running = false
        super.onDestroy()
    }

    private fun startPollingLoop() {
        thread {
            while (running) {
                try {
                    Log.d("Gateway", "polling...")
                    val response = httpGet(pollUrl)
                    Log.d("Gateway", "response: $response")
                    processResponse(response)
                } catch (e: Exception) {
                    Log.e("Gateway", "poll error: ${e.message}")
                }
                try { Thread.sleep(pollIntervalMs) } catch (_: InterruptedException) {}
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
        val trimmed = response.trim()
        if (trimmed == "[]") return

        try {
            val json = JSONObject(trimmed)
            val phone = json.optString("phone", "")
            val message = json.optString("message", "")
            if (phone.isEmpty() || message.isEmpty()) return

            Log.d("Gateway", "JOB: -> $phone")
            val sender = SmsSender(this)
            val ok = sender.send(phone, message)
            Log.d("Gateway", if (ok) "sent" else "send failed")
        } catch (e: Exception) {
            Log.d("Gateway", "skip: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SMS Gateway",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
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
            .setContentTitle("SMS Gateway running")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }
}
