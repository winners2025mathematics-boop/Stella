package com.winchat.smsgateway;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Gateway";
    private static final String POLL_URL = "https://winnersonlineschool.com/winners/poll.php";
    private static final String AUTH_KEY = "winners_gw_9f3a8c2e1b7d4f6a0c8e2d5b9a1f3c7e";
    private static final int POLL_INTERVAL_MS = 20000;
    private static final int PERM_REQUEST = 1001;

    private TextView logView;
    private boolean running = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("SMS Gateway");
        title.setTextSize(22f);
        layout.addView(title);

        TextView status = new TextView(this);
        status.setText("Polls every 20 seconds. Sends SMS via default SIM.");
        status.setTextSize(13f);
        layout.addView(status);

        Button testBtn = new Button(this);
        testBtn.setText("Send Test SMS");
        testBtn.setOnClickListener(v -> sendTestSms());
        layout.addView(testBtn);

        logView = new TextView(this);
        logView.setTextSize(11f);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);
        layout.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(layout);

        appendLog("App started");
        requestPermissions();
    }

    private void sendTestSms() {
        appendLog("TEST: sending to 265991234567");
        boolean ok = sendSms("265991234567", "Test at " + System.currentTimeMillis());
        appendLog(ok ? "TEST queued" : "TEST failed");
    }

    private void requestPermissions() {
        java.util.List<String> needed = new java.util.ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.SEND_SMS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (needed.isEmpty()) {
            appendLog("Permissions OK");
            startPolling();
        } else {
            appendLog("Requesting permissions...");
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERM_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERM_REQUEST) {
            boolean allOk = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allOk = false;
                    break;
                }
            }
            if (allOk) {
                appendLog("All permissions granted");
                startPolling();
            } else {
                appendLog("X permission denied");
            }
        }
    }

    private void startPolling() {
        if (running) return;
        running = true;
        appendLog("Polling started");

        new Thread(() -> {
            while (running) {
                try {
                    String response = httpGet(POLL_URL);
                    appendLog("poll: " + (response.length() > 80 ? response.substring(0, 80) : response));
                    processResponse(response);
                } catch (Exception e) {
                    appendLog("poll error: " + e.getMessage());
                    Log.e(TAG, "poll error", e);
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Gateway-Key", AUTH_KEY);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                return "HTTP " + code;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private void processResponse(String response) {
        if (response == null) return;
        String trimmed = response.trim();
        if (trimmed.isEmpty() || trimmed.equals("[]")) return;
        if (!trimmed.startsWith("{")) return;

        try {
            JSONObject json = new JSONObject(trimmed);
            String phone = json.optString("phone", "");
            String message = json.optString("message", "");
            if (phone.isEmpty() || message.isEmpty()) return;

            appendLog("JOB -> " + phone);
            boolean ok = sendSms(phone, message);
            appendLog(ok ? "SMS queued to " + phone : "SMS failed");
        } catch (Exception e) {
            appendLog("parse error: " + e.getMessage());
        }
    }

    private boolean sendSms(String phone, String message) {
        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }
            if (smsManager == null) return false;

            java.util.ArrayList<String> parts = smsManager.divideMessage(message);
            if (parts.size() > 1) {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(phone, null, message, null, null);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "sendSms failed", e);
            appendLog("sendSms error: " + e.getMessage());
            return false;
        }
    }

    private void appendLog(final String line) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        final String formatted = ts + "  " + line;
        Log.d(TAG, formatted);
        uiHandler.post(() -> {
            if (logView != null) {
                logView.append("\n" + formatted);
            }
        });
    }
}
