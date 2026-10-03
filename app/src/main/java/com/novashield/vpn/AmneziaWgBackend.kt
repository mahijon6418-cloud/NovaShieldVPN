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
        ) { proxy, method, args ->
            when (method.name) {
                "getName" -> "nova-awg"
                "onStateChange" -> Unit
                "isIpv4ResolutionPreferred" -> true
                "isMetered" -> false
                "toString" -> "nova-awg"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> args?.firstOrNull() === proxy
                else -> defaultValue(method.returnType)
            }
        } as Tunnel
    }

    companion object {
        private fun createBackend(context: Context): GoBackend {
            val handler = Proxy.newProxyInstance(
                ClassLoader.getSystemClassLoader(),
                arrayOf(Class.forName("org.amnezia.awg.backend.TunnelActionHandler"))
            ) { _, method, _ ->
                if (method.returnType == Void.TYPE) Unit else defaultValue(method.returnType)
            }

            val constructor = GoBackend::class.java.getConstructor(
                Context::class.java,
                Class.forName("org.amnezia.awg.backend.TunnelActionHandler")
            )
            return constructor.newInstance(context, handler) as GoBackend
        }

        private fun defaultValue(type: Class<*>): Any? = when {
            type == Boolean::class.javaPrimitiveType -> false
            type == Byte::class.javaPrimitiveType -> 0.toByte()
            type == Short::class.javaPrimitiveType -> 0.toShort()
            type == Int::class.javaPrimitiveType -> 0
            type == Long::class.javaPrimitiveType -> 0L
            type == Float::class.javaPrimitiveType -> 0f
            type == Double::class.javaPrimitiveType -> 0.0
            type == Char::class.javaPrimitiveType -> '\u0000'
            type == Void.TYPE -> Unit
            else -> null
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
