package com.simbridge.host.session

import java.net.Inet4Address
import java.net.NetworkInterface

/** Private IPv4 addresses on Wi-Fi / hotspot interfaces; cellular interfaces are excluded. */
object NetworkAddresses {
    private val excludedPrefixes = listOf("rmnet", "ccmni", "v4-", "dummy", "tun", "ppp", "ims", "lo")

    fun localIpv4(limit: Int = 4): List<String> {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
        return interfaces
            .filter { nif -> runCatching { nif.isUp && !nif.isLoopback && !nif.isVirtual }.getOrDefault(false) }
            .filter { nif -> excludedPrefixes.none { nif.name.startsWith(it) } }
            .sortedBy { nif -> if (nif.name.startsWith("wlan")) 0 else 1 }
            .flatMap { nif -> nif.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
            .take(limit)
    }
}
