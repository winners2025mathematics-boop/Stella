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

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "SMS Gateway"
            textSize = 22f
        }
        layout.addView(title)

        val status = TextView(this).apply {
            text = "Polls every 20 seconds. Sends SMS via default SIM."
            textSize = 13f
            setPadding(0, 12, 0, 24)
        }
        layout.addView(status)

        val testBtn = Button(this).apply {
            text = "Send Test SMS"
            setOnClickListener { sendTestSms() }
        }
        layout.addView(testBtn)

        textView = TextView(this).apply {
            textSize = 11f
            setPadding(0, 24, 0, 0)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val scroll = ScrollView(this).apply {
            addView(textView)
        }
        layout.addView(scroll)

        setContentView(layout)

        textView?.text = logLines.joinToString("\n")

        requestNeededPermissions()
    }

    private fun sendTestSms() {
        appendLog("TEST: sending to 265991234567")
        val sender = SmsSender(this)
        val ok = sender.send("265991234567", "Test at ${System.currentTimeMillis()}")
        appendLog(if (ok) "TEST queued" else "TEST failed")
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
            appendLog("Requesting permissions...")
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), PERM_REQUEST)
        } else {
            appendLog("Permissions already granted")
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
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                appendLog("All permissions granted")
                startGateway()
            } else {
                appendLog("X permission denied")
            }
        }
    }

    private fun startGateway() {
        appendLog("Starting gateway service...")
        try {
            val intent = Intent(this, GatewayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            appendLog("Gateway service started")
        } catch (e: Exception) {
            appendLog("X start failed: ${e.message}")
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
