package com.novashield.vpn

object ConfigEditor {
    fun withDns(config: String, dns: String): String {
        val dnsLine = "DNS = " + dns.split(",").map { it.trim() }
            .filter { it.isNotEmpty() }.joinToString(", ")

        val lines = config.lines().toMutableList()
        var inInterface = false
        var replaced = false

        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.equals("[Interface]", ignoreCase = true)) {
                inInterface = true
                continue
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) inInterface = false
            if (inInterface && trimmed.startsWith("DNS", ignoreCase = true) &&
                trimmed.substringAfter("DNS", "").trimStart().startsWith("=")) {
                lines[i] = dnsLine
                replaced = true
                break
            }
        }

        if (!replaced) {
            val interfaceIndex = lines.indexOfFirst {
                it.trim().equals("[Interface]", ignoreCase = true)
            }
            if (interfaceIndex >= 0) {
                val peerIndex = (interfaceIndex + 1 until lines.size).firstOrNull {
                    lines[it].trim().equals("[Peer]", ignoreCase = true)
                } ?: lines.size
                lines.add(peerIndex, dnsLine)
            }
        }
        return lines.joinToString("\n")
    }
}
