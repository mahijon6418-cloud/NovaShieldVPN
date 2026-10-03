package com.novashield.vpn

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
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
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private val backend by lazy { AmneziaWgBackend(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        title = findViewById(R.id.title)
        subtitle = findViewById(R.id.subtitle)
        configInfo = findViewById(R.id.config_info)
        dnsInfo = findViewById(R.id.dns_info)
        connect = findViewById(R.id.connect)

        resetDisconnectedUi()
        findViewById<android.view.View>(R.id.root).setBackgroundColor(0xFFF5F7FB.toInt())

        findViewById<Button>(R.id.import_config).setOnClickListener { importConfig() }
        findViewById<Button>(R.id.import_clipboard).setOnClickListener { importFromClipboard() }
        findViewById<Button>(R.id.change_dns).setOnClickListener { changeDns() }
        connect.setOnClickListener {
            if (backend.isConnected()) disconnect() else connect()
        }

        render()
    }

    private fun importFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "کلیپ‌بورد خالی است", Toast.LENGTH_SHORT).show()
            return
        }
        val clip = clipboard.primaryClip ?: return
        val text = clip.getItemAt(0).coerceToText(this).toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "کلیپ‌بورد شامل کانفیگ نیست", Toast.LENGTH_SHORT).show()
            return
        }
        executor.execute {
            try {
                backend.validate(text)
                saveConfig(text)
                runOnUiThread {
                    status.text = "کانفیگ با موفقیت وارد شد"
                    status.setTextColor(0xff18864b.toInt())
                    render()
                    Toast.makeText(this, "کانفیگ از کلیپ‌بورد وارد شد", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { showConnectionFailure("کانفیگ نامعتبر است") }
            }
        }
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
                saveConfig(text)
                runOnUiThread {
                    status.text = "کانفیگ با موفقیت وارد شد"
                    status.setTextColor(0xff18864b.toInt())
                    render()
                    Toast.makeText(this, "کانفیگ با موفقیت وارد شد", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { showConnectionFailure("کانفیگ نامعتبر است") }
            }
        }
    }

    private fun connect() {
        val config = savedConfig()
        if (config == null) {
            showConnectionFailure("متصل نمی‌شود")
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
        setConnectingUi()
        executor.execute {
            try {
                backend.connect(config)

                // A successful setState(UP) only means the tunnel was requested.
                // Do not show green until traffic actually works through the VPN.
                val realConnection = waitForRealVpnConnection()
                if (!realConnection) {
                    runCatching { backend.disconnect() }
                    runOnUiThread { showConnectionFailure("متصل نمی‌شود") }
                    return@execute
                }

                runOnUiThread { showConnectedUi() }
            } catch (e: Exception) {
                runCatching { backend.disconnect() }
                runOnUiThread { showConnectionFailure("متصل نمی‌شود") }
            }
        }
    }

    private fun waitForRealVpnConnection(): Boolean {
        repeat(6) {
            if (hasVpnTransport() && probeInternetThroughVpn()) return true
            Thread.sleep(1000)
        }
        return false
    }

    private fun hasVpnTransport(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.allNetworks.any { network ->
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    private fun probeInternetThroughVpn(): Boolean {
        val endpoints = listOf(
            "https://www.cloudflare.com/cdn-cgi/trace",
            "https://cloudflare.com/cdn-cgi/trace"
        )
        for (endpoint in endpoints) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.connectTimeout = 3500
                connection.readTimeout = 3500
                connection.instanceFollowRedirects = true
                connection.useCaches = false
                connection.requestMethod = "GET"
                val code = connection.responseCode
                if (code in 200..399) return true
            } catch (_: Exception) {
                // Try the next endpoint.
            } finally {
                connection?.disconnect()
            }
        }
        return false
    }

    private fun disconnect() {
        status.text = "در حال قطع اتصال…"
        executor.execute {
            try {
                backend.disconnect()
                runOnUiThread { resetDisconnectedUi() }
            } catch (_: Exception) {
                runOnUiThread { showConnectionFailure("متصل نمی‌شود") }
            }
        }
    }

    private fun changeDns() {
        val current = getSharedPreferences(prefsName, 0)
            .getString(dnsKey, "1.1.1.1, 8.8.8.8") ?: "1.1.1.1, 8.8.8.8"

        val input = EditText(this).apply {
            hint = "1.1.1.1, 8.8.8.8"
            setText(current)
            setSelectAllOnFocus(true)
        }

        AlertDialog.Builder(this)
            .setTitle("سرورهای DNS")
            .setMessage("یک یا چند نشانی IP برای DNS را با ویرگول وارد کنید.")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                val dns = input.text.toString().trim()
                try {
                    backend.validateDns(dns)
                    getSharedPreferences(prefsName, 0).edit().putString(dnsKey, dns).apply()
                    savedConfig()?.let { config ->
                        val updated = ConfigEditor.withDns(config, dns)
                        saveConfig(updated)
                    }
                    status.text = "DNS تغییر کرد"
                    status.setTextColor(0xff18864b.toInt())
                    render()
                } catch (e: Exception) {
                    Toast.makeText(this, e.message ?: "DNS نامعتبر است", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun saveConfig(config: String) {
        getSharedPreferences(prefsName, 0).edit().putString(configKey, config).apply()
    }

    private fun savedConfig(): String? =
        getSharedPreferences(prefsName, 0).getString(configKey, null)

    private fun render() {
        val config = savedConfig()
        val dns = getSharedPreferences(prefsName, 0)
            .getString(dnsKey, "1.1.1.1, 8.8.8.8") ?: "1.1.1.1, 8.8.8.8"
        configInfo.text = if (config == null) "هیچ کانفیگی وارد نشده است" else "کانفیگ وارد شده"
        dnsInfo.text = "DNS: $dns"
        connect.isEnabled = config != null
    }

    private fun setConnectingUi() {
        status.text = "در حال بررسی اتصال…"
        status.setTextColor(0xffffffff.toInt())
        connect.text = "در حال اتصال…"
        connect.isEnabled = false
        findViewById<android.view.View>(R.id.root).setBackgroundColor(0xffd97706.toInt())
        title.setTextColor(0xffffffff.toInt())
        subtitle.setTextColor(0xffffffff.toInt())
        configInfo.setTextColor(0xffffffff.toInt())
        dnsInfo.setTextColor(0xffffffff.toInt())
    }

    private fun showConnectedUi() {
        status.text = "متصل شد"
        status.setTextColor(0xffffffff.toInt())
        connect.text = "قطع اتصال"
        connect.isEnabled = true
        findViewById<android.view.View>(R.id.root).setBackgroundColor(0xff18864b.toInt())
        title.setTextColor(0xffffffff.toInt())
        subtitle.setTextColor(0xffffffff.toInt())
        configInfo.setTextColor(0xffffffff.toInt())
        dnsInfo.setTextColor(0xffffffff.toInt())
    }

    private fun showConnectionFailure(message: String) {
        status.text = message
        status.setTextColor(0xffffffff.toInt())
        connect.text = "اتصال"
        connect.isEnabled = savedConfig() != null
        findViewById<android.view.View>(R.id.root).setBackgroundColor(0xffd97706.toInt())
        title.setTextColor(0xffffffff.toInt())
        subtitle.setTextColor(0xffffffff.toInt())
        configInfo.setTextColor(0xffffffff.toInt())
        dnsInfo.setTextColor(0xffffffff.toInt())
    }

    private fun resetDisconnectedUi() {
        status.text = "قطع است"
        status.setTextColor(0xff667085.toInt())
        title.setTextColor(0xff15213A.toInt())
        subtitle.setTextColor(0xff667085.toInt())
        configInfo.setTextColor(0xff15213A.toInt())
        dnsInfo.setTextColor(0xff15213A.toInt())
        connect.text = "اتصال"
        connect.isEnabled = true
        findViewById<android.view.View>(R.id.root).setBackgroundColor(0xffF5F7FB.toInt())
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
