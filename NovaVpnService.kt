package com.novashield.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class NovaVpnService : VpnService() {
    companion object {
        @Volatile var running = false
        const val ACTION_STATUS = "com.novashield.vpn.STATUS"
    }

    private var iface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (iface == null) {
            broadcast("connecting")
            iface = Builder().setSession("NovaShield")
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .establish()

            if (iface == null) {
                running = false
                broadcast("failed")
                return START_NOT_STICKY
            }

            running = true
            thread {
                val connected = probeRealConnectivity()
                broadcast(if (connected) "connected" else "failed")
            }
        }
        return START_STICKY
    }

    private fun probeRealConnectivity(): Boolean {
        return try {
            val connection = (URL("https://www.gstatic.com/generate_204").openConnection() as HttpURLConnection)
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connect()
            val ok = connection.responseCode in 200..399
            connection.disconnect()
            ok
        } catch (_: Exception) {
            false
        }
    }

    private fun broadcast(state: String) {
        sendBroadcast(Intent(ACTION_STATUS).putExtra("state", state))
    }

    override fun onDestroy() {
        iface?.close()
        iface = null
        running = false
        broadcast("disconnected")
        super.onDestroy()
    }
}