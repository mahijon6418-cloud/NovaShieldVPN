package com.novashield.vpn

/** A transport implementation supplied by an authorized VPN backend. */
interface VpnBackend {
    fun connect(server: ServerInfo)
    fun disconnect()
    fun isConnected(): Boolean
}

data class ServerInfo(
    val countryFlag: String,
    val countryName: String,
    val city: String,
    val availability: ServerAvailability = ServerAvailability.CONFIGURATION_REQUIRED,
) {
    val displayName: String
        get() = "$countryFlag $countryName — $city"
}

enum class ServerAvailability(val label: String) {
    CONFIGURATION_REQUIRED("Configuration required"),
}

/**
 * Extension point for a legitimate WireGuard integration. No tunnel is started
 * until an authorized WireGuard backend and server configuration are supplied.
 */
class WireGuardBackend : VpnBackend {
    private var connected = false

    override fun connect(server: ServerInfo) {
        connected = false
    }

    override fun disconnect() {
        connected = false
    }

    override fun isConnected() = connected

    fun statusMessage(server: ServerInfo) =
        "WireGuard requires an authorized backend configuration for ${server.displayName}."
}

/**
 * Extension point for a legitimate OpenVPN integration. No credentials or
 * connection details are bundled with this application.
 */
class OpenVpnBackend : VpnBackend {
    private var connected = false

    override fun connect(server: ServerInfo) {
        connected = false
    }

    override fun disconnect() {
        connected = false
    }

    override fun isConnected() = connected

    fun statusMessage(server: ServerInfo) =
        "OpenVPN requires an authorized backend configuration for ${server.displayName}."
}

/** Safe placeholder: this build contains no proprietary Mimic implementation. */
class MimicBackend : VpnBackend {
    override fun connect(server: ServerInfo) = Unit

    override fun disconnect() = Unit

    override fun isConnected() = false

    fun statusMessage() = "Official Mimic SDK required"
}
