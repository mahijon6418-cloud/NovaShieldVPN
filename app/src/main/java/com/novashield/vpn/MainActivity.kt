package com.novashield.vpn

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val defaultCode = "L69F4D-GGDYQ2-5XUJSA"
    private val servers = listOf(
        ServerInfo("🇩🇪", "Germany", "Frankfurt"),
        ServerInfo("🇳🇱", "Netherlands", "Amsterdam"),
        ServerInfo("🇬🇧", "United Kingdom", "London"),
        ServerInfo("🇫🇷", "France", "Paris"),
        ServerInfo("🇹🇷", "Türkiye", "Istanbul"),
        ServerInfo("🇫🇮", "Finland", "Helsinki"),
        ServerInfo("🇨🇦", "Canada", "Toronto"),
        ServerInfo("🇯🇵", "Japan", "Tokyo"),
        ServerInfo("🇦🇺", "Australia", "Sydney"),
    )
    private val protocolNames = listOf("WireGuard", "OpenVPN", "Mimic")
    private val backends = mapOf(
        "WireGuard" to WireGuardBackend(),
        "OpenVPN" to OpenVpnBackend(),
        "Mimic" to MimicBackend(),
    )

    private lateinit var status: TextView
    private lateinit var protocolStatus: TextView
    private lateinit var serverStatus: TextView
    private lateinit var connect: Button
    private lateinit var selectedServer: ServerInfo
    private var selectedProtocol = "WireGuard"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        protocolStatus = findViewById(R.id.protocol_status)
        serverStatus = findViewById(R.id.server_status)
        connect = findViewById(R.id.connect)
        selectedServer = servers.first()

        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
        if (!prefs.contains("access_code")) {
            prefs.edit().putString("access_code", defaultCode).apply()
        }

        setUpProtocolSelector()
        setUpServerSelector()
        updateServerStatus()
        connect.setOnClickListener { connectSelectedBackend() }
        findViewById<Button>(R.id.code).setOnClickListener { editCode() }
    }

    private fun setUpProtocolSelector() {
        val selector = findViewById<Spinner>(R.id.protocol_selector)
        selector.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, protocolNames)
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedProtocol = protocolNames[position]
                updateProtocolStatus()
                setDisconnected()
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    private fun setUpServerSelector() {
        val selector = findViewById<Spinner>(R.id.server_selector)
        selector.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            servers.map { "${it.displayName} — ${it.availability.label}" },
        )
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedServer = servers[position]
                updateServerStatus()
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    private fun updateServerStatus() {
        serverStatus.text = "Selected server: ${selectedServer.displayName}\n${selectedServer.availability.label}"
    }

    private fun updateProtocolStatus() {
        protocolStatus.text = when (selectedProtocol) {
            "Mimic" -> "Protocol: Mimic — Requires licensed/official Mimic SDK"
            else -> "Protocol: $selectedProtocol — Available for an authorized backend"
        }
    }

    private fun connectSelectedBackend() {
        val backend = checkNotNull(backends[selectedProtocol])
        if (backend.isConnected()) {
            backend.disconnect()
            setDisconnected()
            return
        }
        backend.connect(selectedServer)
        val message = when (backend) {
            is MimicBackend -> "Mimic is not available in this build."
            is WireGuardBackend -> backend.statusMessage(selectedServer)
            is OpenVpnBackend -> backend.statusMessage(selectedServer)
            else -> "No backend is configured."
        }
        status.text = message
        status.setTextColor(0xffc0392b.toInt())
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setDisconnected() {
        status.text = "Disconnected"
        status.setTextColor(0xffc0392b.toInt())
        connect.text = "CONNECT"
    }

    private fun editCode() {
        val input = EditText(this)
        input.hint = "Access code"
        input.setText(getSharedPreferences("config", Context.MODE_PRIVATE).getString("access_code", defaultCode))
        AlertDialog.Builder(this)
            .setTitle("Access code")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                getSharedPreferences("config", Context.MODE_PRIVATE)
                    .edit()
                    .putString("access_code", input.text.toString().trim())
                    .apply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
