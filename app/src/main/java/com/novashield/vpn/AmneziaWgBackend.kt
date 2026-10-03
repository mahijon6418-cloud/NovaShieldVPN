package com.novashield.vpn

import android.content.Context
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream
import java.net.InetAddress

class AmneziaWgBackend(context: Context) {
    private val backend = GoBackend(context.applicationContext)
    private var tunnel: Tunnel? = null

    fun validate(configText: String) {
        Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))
    }

    fun validateDns(value: String) {
        val dns = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (dns.isEmpty()) throw IllegalArgumentException("Enter at least one DNS server.")
        dns.forEach {
            try { InetAddress.getByName(it) }
            catch (_: Exception) { throw IllegalArgumentException("Invalid DNS address: $it") }
        }
    }

    fun connect(configText: String) {
        val config = Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))
        val activeTunnel = tunnel ?: object : Tunnel {
            override fun getName(): String = "nova-awg"
            override fun onStateChange(newState: Tunnel.State) {}
        }.also { tunnel = it }
        backend.setState(activeTunnel, Tunnel.State.UP, config)
    }

    fun disconnect() {
        tunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) }
    }

    fun isConnected(): Boolean =
        tunnel?.let { backend.getState(it) == Tunnel.State.UP } == true

    companion object {
        fun detectVersion(config: String): String = when {
            Regex("(?m)^\s*HeaderProtectionKey\s*=").containsMatchIn(config) ||
            Regex("(?m)^\s*ContentPaddingAddition\s*=").containsMatchIn(config) ->
                "AmneziaWG 3.x"
            Regex("(?m)^\s*(Jc|Jmin|Jmax|H1|H2|H3|H4)\s*=").containsMatchIn(config) ->
                "AmneziaWG 2.0"
            else -> "WireGuard-compatible config"
        }
    }
}
