package com.winchat.smsgateway

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        val logLines = mutableListOf<String>()
        var textView: TextView? = null
    }

    private val PERM_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build UI programmatically (no XML needed)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "SMS Gateway"
            textSize = 20f
        }
        layout.addView(title)

        val status = TextView(this).apply {
            text = "Polling every 20s"
            textSize = 12f
            setPadding(0, 8, 0, 16)
        }
        layout.addView(status)

        val testBtn = Button(this).apply {
            text = "Send Test SMS to 265991234567"
            setOnClickListener {
                sendTestSms()
            }
        }
        layout.addView(testBtn)

        textView = TextView(this).apply {
            textSize = 11f
            setPadding(0, 16, 0, 0)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val scroll = ScrollView(this).apply {
            addView(textView)
        }
        layout.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        setContentView(layout)

        // Sync log with service logs
        textView?.text = logLines.joinToString("\n")

        // Request permissions
        requestNeededPermissions()
    }

    private fun sendTestSms() {
        appendLog("TEST: sending to 265991234567")
        val sender = SmsSender(this)
        sender.send("265991234567", "Test from gateway at ${System.currentTimeMillis()}")
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.SEND_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), PERM_REQUEST)
        } else {
            startGateway()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startGateway()
            } else {
                appendLog("X permissions denied")
            }
        }
    }

    private fun startGateway() {
        appendLog("Starting gateway service...")
        val intent = Intent(this, GatewayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    fun appendLog(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val formatted = "$ts  $line"
        logLines.add(formatted)
        if (logLines.size > 500) logLines.removeAt(0)

        runOnUiThread {
            textView?.append("\n$formatted")
        }
    }
}
