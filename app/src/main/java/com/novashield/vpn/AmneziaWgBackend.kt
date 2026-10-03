package com.novashield.vpn

import android.content.Context
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream
import java.lang.reflect.Proxy
import java.net.InetAddress

class AmneziaWgBackend(context: Context) {
    private val backend: GoBackend = createBackend(context.applicationContext)
    private var tunnel: Tunnel? = null

    fun validate(configText: String) {
        Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))
    }

    fun validateDns(value: String) {
        val dns = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (dns.isEmpty()) throw IllegalArgumentException("Enter at least one DNS server.")
        dns.forEach {
            try {
                InetAddress.getByName(it)
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid DNS address: $it")
            }
        }
    }

    fun connect(configText: String) {
        val config = Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))
        val activeTunnel = tunnel ?: createTunnel().also { tunnel = it }
        backend.setState(activeTunnel, Tunnel.State.UP, config)
    }

    fun disconnect() {
        tunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) }
    }

    fun isConnected(): Boolean =
        tunnel?.let { backend.getState(it) == Tunnel.State.UP } == true

    private fun createTunnel(): Tunnel {
        return Proxy.newProxyInstance(
            Tunnel::class.java.classLoader,
            arrayOf(Tunnel::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getName" -> "nova-awg"
                "onStateChange" -> Unit
                "toString" -> "nova-awg"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> args?.firstOrNull() === this
                else -> null
            }
        } as Tunnel
    }

    companion object {
        private fun createBackend(context: Context): GoBackend {
            val constructors = GoBackend::class.java.constructors
                .sortedBy { it.parameterTypes.size }
            val constructor = constructors.firstOrNull()
                ?: error("No GoBackend constructor found")
            val args = constructor.parameterTypes.mapIndexed { index, type ->
                when {
                    index == 0 && Context::class.java.isAssignableFrom(type) -> context
                    type == Boolean::class.javaPrimitiveType -> false
                    type == Int::class.javaPrimitiveType -> 0
                    type == Long::class.javaPrimitiveType -> 0L
                    type.isEnum -> type.enumConstants?.firstOrNull()
                    else -> null
                }
            }.toTypedArray()
            return constructor.newInstance(*args) as GoBackend
        }

        fun detectVersion(config: String): String = when {
            Regex("""(?m)^\s*HeaderProtectionKey\s*=""").containsMatchIn(config) ||
            Regex("""(?m)^\s*ContentPaddingAddition\s*=""").containsMatchIn(config) ->
                "AmneziaWG 3.x"
            Regex("""(?m)^\s*(Jc|Jmin|Jmax|H1|H2|H3|H4)\s*=""").containsMatchIn(config) ->
                "AmneziaWG 2.0"
            else -> "WireGuard-compatible config"
        }
    }
}
