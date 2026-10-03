package com.novashield.vpn

import android.app.*
import android.content.*
import android.net.VpnService
import android.os.Bundle
import android.widget.*

class MainActivity : Activity() {
    private val defaultCode = "L69F4D-GGDYQ2-5XUJSA"
    private lateinit var status: TextView
    private lateinit var connect: Button

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra("state")) {
                "connecting" -> setConnecting()
                "connected" -> setConnected()
                "failed" -> setFailed()
                "disconnected" -> setDisconnected()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        connect = findViewById(R.id.connect)
        val prefs = getSharedPreferences("config", 0)
        if (!prefs.contains("access_code")) prefs.edit().putString("access_code", defaultCode).apply()

        registerReceiver(statusReceiver, IntentFilter(NovaVpnService.ACTION_STATUS), RECEIVER_NOT_EXPORTED)

        connect.setOnClickListener {
            if (NovaVpnService.running) {
                stopService(Intent(this, NovaVpnService::class.java))
                setDisconnected()
            } else {
                val intent = VpnService.prepare(this)
                if (intent != null) startActivityForResult(intent, 7) else startVpn()
            }
        }
        findViewById<Button>(R.id.code).setOnClickListener { editCode() }
    }

    private fun startVpn() {
        startService(Intent(this, NovaVpnService::class.java))
        setConnecting()
    }

    private fun setConnecting() {
        status.text = "در حال اتصال…"
        status.setTextColor(0xff315cff.toInt())
        connect.text = "DISCONNECT"
    }

    private fun setConnected() {
        status.text = "متصل"
        status.setTextColor(0xff188038.toInt())
        connect.text = "DISCONNECT"
    }

    private fun setFailed() {
        status.text = "متصل نمی‌شود"
        status.setTextColor(0xfff39c12.toInt())
        connect.text = "CONNECT"
    }

    private fun setDisconnected() {
        status.text = "قطع شده"
        status.setTextColor(0xffc0392b.toInt())
        connect.text = "CONNECT"
    }

    override fun onActivityResult(r: Int, c: Int, d: Intent?) {
        super.onActivityResult(r, c, d)
        if (r == 7 && c == RESULT_OK) startVpn()
    }

    override fun onDestroy() {
        unregisterReceiver(statusReceiver)
        super.onDestroy()
    }

    private fun editCode() {
        val input = EditText(this)
        input.hint = "Access code"
        input.setText(getSharedPreferences("config", 0).getString("access_code", defaultCode))
        AlertDialog.Builder(this).setTitle("Access code").setView(input)
            .setPositiveButton("Save") { _, _ ->
                getSharedPreferences("config", 0).edit().putString("access_code", input.text.toString().trim()).apply()
            }
            .setNegativeButton("Cancel", null).show()
    }
}
