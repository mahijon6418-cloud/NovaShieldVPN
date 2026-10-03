package com.novashield.vpn

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val prefsName = "amneziawg"
    private val configKey = "config"
    private val dnsKey = "dns"

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var configInfo: TextView
    private lateinit var dnsInfo: TextView
    private lateinit var connect: Button

    private val backend by lazy { AmneziaWgBackend(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        configInfo = findViewById(R.id.config_info)
        dnsInfo = findViewById(R.id.dns_info)
        connect = findViewById(R.id.connect)

        findViewById<Button>(R.id.import_config).setOnClickListener { importConfig() }
        findViewById<Button>(R.id.change_dns).setOnClickListener { changeDns() }
        connect.setOnClickListener {
            if (backend.isConnected()) disconnect() else connect()
        }

        render()
    }

    private fun importConfig() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK) return

        val uri = data?.data ?: return
        executor.execute {
            try {
                val text = contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw IllegalStateException("Could not read configuration file")

                backend.validate(text)

                getSharedPreferences(prefsName, 0)
                    .edit()
                    .putString(configKey, text)
                    .apply()

                runOnUiThread {
                    status.text = "Configuration imported"
                    status.setTextColor(0xff18864b.toInt())
                    render()
                    Toast.makeText(this, "AmneziaWG configuration imported", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Invalid configuration"
                    status.setTextColor(0xffc0392b.toInt())
                    Toast.makeText(
                        this,
                        e.message ?: "Configuration import failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun connect() {
        val config = savedConfig()
        if (config == null) {
            status.text = "Import a configuration first"
            status.setTextColor(0xffb54708.toInt())
            return
        }

        val permission = VpnService.prepare(this)
        if (permission != null) {
            startActivityForResult(permission, REQUEST_VPN_PERMISSION)
            return
        }

        startTunnel(config)
    }

    private fun startTunnel(config: String) {
        status.text = "Connecting…"
        status.setTextColor(0xff315cff.toInt())
        connect.text = "DISCONNECT"

        executor.execute {
            try {
                backend.connect(config)
                runOnUiThread {
                    status.text = "Connected"
                    status.setTextColor(0xff18864b.toInt())
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Connection failed"
                    status.setTextColor(0xffc0392b.toInt())
                    connect.text = "CONNECT"
                    Toast.makeText(
                        this,
                        e.message ?: "AmneziaWG connection failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun disconnect() {
        status.text = "Disconnecting…"
        executor.execute {
            try {
                backend.disconnect()
                runOnUiThread {
                    status.text = "Disconnected"
                    status.setTextColor(0xff667085.toInt())
                    connect.text = "CONNECT"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Disconnect failed"
                    status.setTextColor(0xffc0392b.toInt())
                }
            }
        }
    }

    private fun changeDns() {
        val current = getSharedPreferences(prefsName, 0)
            .getString(dnsKey, "1.1.1.1, 8.8.8.8")
            ?: "1.1.1.1, 8.8.8.8"

        val input = EditText(this).apply {
            hint = "1.1.1.1, 8.8.8.8"
            setText(current)
            setSelectAllOnFocus(true)
        }

        AlertDialog.Builder(this)
            .setTitle("DNS servers")
            .setMessage("Enter one or more DNS IP addresses separated by commas.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val dns = input.text.toString().trim()
                try {
                    backend.validateDns(dns)
                    getSharedPreferences(prefsName, 0)
                        .edit()
                        .putString(dnsKey, dns)
                        .apply()

                    val config = savedConfig()
                    if (config != null) {
                        val updated = ConfigEditor.withDns(config, dns)
                        getSharedPreferences(prefsName, 0)
                            .edit()
                            .putString(configKey, updated)
                            .apply()
                        status.text = "DNS updated"
                        status.setTextColor(0xff18864b.toInt())
                    }
                    render()
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        e.message ?: "Invalid DNS",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savedConfig(): String? =
        getSharedPreferences(prefsName, 0).getString(configKey, null)

    private fun render() {
        val config = savedConfig()
        val dns = getSharedPreferences(prefsName, 0)
            .getString(dnsKey, "1.1.1.1, 8.8.8.8")
            ?: "1.1.1.1, 8.8.8.8"

        configInfo.text = if (config == null) {
            "No configuration imported"
        } else {
            "Imported • " + AmneziaWgBackend.detectVersion(config)
        }

        dnsInfo.text = "DNS: $dns"
        connect.isEnabled = config != null
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_IMPORT = 10
        private const val REQUEST_VPN_PERMISSION = 11
    }
}
