package com.kart1kg.raven.util

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Utility to discover the phone's hotspot/tethering IP address.
 *
 * Android hotspot interfaces are typically named:
 * - "ap0", "wlan1", "swlan0" (varies by OEM)
 * - Or any non-loopback, non-rmnet (cellular) interface with a 192.168.x.x address
 *
 * Falls back to any non-loopback IPv4 address if no tethering interface is found.
 */
object NetworkUtils {

    private val HOTSPOT_INTERFACE_PREFIXES = listOf("ap", "wlan", "swlan", "rndis", "eth")
    private val HOTSPOT_SUBNETS = listOf("192.168.43.", "192.168.49.", "192.168.44.")

    /**
     * Returns the phone's hotspot IP address, or a best-guess local IP.
     * Returns null if no suitable network interface is found.
     */
    fun getHotspotIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null

            var fallbackIp: String? = null

            for (iface in interfaces.asSequence()) {
                if (!iface.isUp || iface.isLoopback) continue

                for (addr in iface.inetAddresses) {
                    if (addr !is Inet4Address || addr.isLoopbackAddress) continue

                    val ip = addr.hostAddress ?: continue

                    // Check if this looks like a hotspot interface
                    val isHotspotInterface = HOTSPOT_INTERFACE_PREFIXES.any {
                        iface.name.startsWith(it, ignoreCase = true)
                    }
                    val isHotspotSubnet = HOTSPOT_SUBNETS.any { ip.startsWith(it) }

                    if (isHotspotInterface && isHotspotSubnet) {
                        return ip
                    }

                    // Prefer hotspot subnet even if interface name doesn't match
                    if (isHotspotSubnet) {
                        return ip
                    }

                    // Keep as fallback (skip cellular rmnet interfaces)
                    if (!iface.name.startsWith("rmnet", ignoreCase = true)) {
                        fallbackIp = ip
                    }
                }
            }

            return fallbackIp
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Returns all non-loopback IPv4 addresses with their interface names.
     * Useful for debugging which interfaces are active.
     */
    fun getAllIpAddresses(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return result
            for (iface in interfaces.asSequence()) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                    val ip = addr.hostAddress ?: continue
                    result.add(iface.name to ip)
                }
            }
        } catch (_: Exception) { }
        return result
    }
}
