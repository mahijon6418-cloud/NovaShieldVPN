package com.novashield.vpn

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
    private lateinit var title: TextView
    private lateinit var subtitle: TextView

    private val backend by lazy { AmneziaWgBackend(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        findViewById<android.view.View>(R.id.root).setBackgroundColor(0xFFF5F7FB.toInt())
        title = findViewById(R.id.title)
        subtitle = findViewById(R.id.subtitle)
        configInfo = findViewById(R.id.config_info)
        dnsInfo = findViewById(R.id.dns_info)
        connect = findViewById(R.id.connect)

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

        val clip = clipboard.primaryClip ?: run {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val text = clip.getItemAt(0).coerceToText(this).toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "کلیپ‌بورد شامل کانفیگ نیست", Toast.LENGTH_SHORT).show()
            return
        }

        executor.execute {
            try {
                backend.validate(text)
                getSharedPreferences(prefsName, 0)
                    .edit()
                    .putString(configKey, text)
                    .apply()

                runOnUiThread {
                    status.text = "کانفیگ با موفقیت وارد شد"
                    status.setTextColor(0xff18864b.toInt())
                    render()
                    Toast.makeText(this, "کانفیگ از کلیپ‌بورد وارد شد", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "کانفیگ نامعتبر است"
                    status.setTextColor(0xffc0392b.toInt())
                    Toast.makeText(
                        this,
                        e.message ?: "کانفیگ کلیپ‌بورد نامعتبر است",
                        Toast.LENGTH_LONG
                    ).show()
                }
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

                getSharedPreferences(prefsName, 0)
                    .edit()
                    .putString(configKey, text)
                    .apply()

                runOnUiThread {
                    status.text = "کانفیگ با موفقیت وارد شد"
                    status.setTextColor(0xff18864b.toInt())
                    render()
                    Toast.makeText(this, "کانفیگ با موفقیت وارد شد", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "کانفیگ نامعتبر است"
                    status.setTextColor(0xffc0392b.toInt())
                    Toast.makeText(
                        this,
                        e.message ?: "وارد کردن کانفیگ ناموفق بود",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun connect() {
        val config = savedConfig()
        if (config == null) {
            status.text = "ابتدا کانفیگ را وارد کنید"
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
        status.text = "در حال اتصال…"
        status.setTextColor(0xff315cff.toInt())
        connect.text = "قطع اتصال"

        executor.execute {
            try {
                backend.connect(config)
                runOnUiThread {
                    status.text = "متصل شد"
                    status.setTextColor(0xffffffff.toInt())
                    title.setTextColor(0xffffffff.toInt())
                    subtitle.setTextColor(0xffffffff.toInt())
                    configInfo.setTextColor(0xffffffff.toInt())
                    dnsInfo.setTextColor(0xffffffff.toInt())
                    findViewById<android.view.View>(R.id.root).setBackgroundColor(0xff18864b.toInt())
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "اتصال ناموفق بود"
                    status.setTextColor(0xffc0392b.toInt())
                    connect.text = "اتصال"
                    Toast.makeText(
                        this,
                        e.message ?: "اتصال ناموفق بود",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun disconnect() {
        status.text = "در حال قطع اتصال…"
        executor.execute {
            try {
                backend.disconnect()
                runOnUiThread {
                    status.text = "قطع شد"
                    status.setTextColor(0xff667085.toInt())
                    title.setTextColor(0xff15213A.toInt())
                    subtitle.setTextColor(0xff667085.toInt())
                    configInfo.setTextColor(0xff15213A.toInt())
                    dnsInfo.setTextColor(0xff15213A.toInt())
                    findViewById<android.view.View>(R.id.root).setBackgroundColor(0xffF5F7FB.toInt())
                    connect.text = "اتصال"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "قطع اتصال ناموفق بود"
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
            .setTitle("سرورهای DNS")
            .setMessage("یک یا چند نشانی IP برای DNS را با ویرگول وارد کنید.")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
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
                        status.text = "DNS تغییر کرد"
                        status.setTextColor(0xff18864b.toInt())
                    }
                    render()
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        e.message ?: "DNS نامعتبر است",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("لغو", null)
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
            "هیچ کانفیگی وارد نشده است"
        } else {
            "کانفیگ وارد شده"
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
