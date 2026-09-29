package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import com.example.data.model.NetworkInfo
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import kotlin.math.min

object NetworkUtils {

    /**
     * Inspects active network interfaces and determines the current Local Network / Hotspot
     * IP, Subnet Mask, Network Address, Prefix, and host scan range.
     * Never assumes fixed 192.168.43.0/24.
     */
    fun detectCurrentNetwork(context: Context? = null): NetworkInfo? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

            // Candidate interfaces with IPv4
            val candidates = mutableListOf<Pair<NetworkInterface, java.net.InterfaceAddress>>()

            for (netIf in interfaces) {
                if (!netIf.isUp || netIf.isLoopback) continue
                for (addr in netIf.interfaceAddresses) {
                    val inetAddr = addr.address
                    if (inetAddr is Inet4Address && !inetAddr.isLoopbackAddress) {
                        candidates.add(netIf to addr)
                    }
                }
            }

            if (candidates.isEmpty()) return null

            // Prioritize Hotspot / AP interfaces (e.g. ap0, softap, wlan, swlan, rndis)
            val selected = candidates.minByOrNull { (netIf, addr) ->
                val name = netIf.name.lowercase()
                val ip = addr.address.hostAddress ?: ""
                when {
                    name.startsWith("ap") || name.startsWith("softap") -> 0
                    ip.startsWith("192.168.43.") -> 1
                    name.startsWith("wlan") -> 2
                    name.startsWith("swlan") || name.startsWith("rndis") -> 3
                    ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.") -> 4
                    else -> 10
                }
            } ?: return null

            val (netIf, ifAddress) = selected
            val ipAddress = ifAddress.address.hostAddress ?: return null
            var prefix = ifAddress.networkPrefixLength.toInt()

            // Safe fallback if prefix is invalid or 0
            if (prefix !in 8..30) {
                prefix = 24
            }

            val ipUInt = ipToUInt(ipAddress)
            val maskUInt = prefixToMask(prefix)
            val networkUInt = ipUInt and maskUInt
            val broadcastUInt = networkUInt or maskUInt.inv()

            val subnetMask = uIntToIp(maskUInt)
            val networkAddress = uIntToIp(networkUInt)
            val broadcastAddress = uIntToIp(broadcastUInt)

            val firstHostUInt = networkUInt + 1u
            val lastHostUInt = if (broadcastUInt > 1u) broadcastUInt - 1u else firstHostUInt

            val hostCount = (lastHostUInt - firstHostUInt + 1u).toInt().coerceAtLeast(1)

            val ifNameLower = netIf.name.lowercase()
            val isHotspot = ifNameLower.startsWith("ap") || ifNameLower.startsWith("softap") || ipAddress.startsWith("192.168.43.")
            val isWifi = ifNameLower.startsWith("wlan") || ifNameLower.startsWith("swlan")

            return NetworkInfo(
                interfaceName = netIf.name,
                ipAddress = ipAddress,
                subnetMask = subnetMask,
                networkAddress = networkAddress,
                broadcastAddress = broadcastAddress,
                prefixLength = prefix,
                cidr = "$networkAddress/$prefix",
                firstHostIp = uIntToIp(firstHostUInt),
                lastHostIp = uIntToIp(lastHostUInt),
                hostCount = hostCount,
                isHotspot = isHotspot,
                isWifi = isWifi
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Generates list of IP addresses to scan in the detected network.
     * Caps scan range to maximum 254 addresses (one /24 subnet slice) to avoid mobile freeze
     * when on large corporate subnets like /16.
     */
    fun calculateHostRange(networkInfo: NetworkInfo): List<String> {
        val ipUInt = ipToUInt(networkInfo.ipAddress)
        val prefix = networkInfo.prefixLength

        // If prefix is /24 or smaller (/24 to /30), scan exact host range
        if (prefix >= 24) {
            val maskUInt = prefixToMask(prefix)
            val networkUInt = ipUInt and maskUInt
            val broadcastUInt = networkUInt or maskUInt.inv()

            val first = networkUInt + 1u
            val last = broadcastUInt - 1u

            val list = mutableListOf<String>()
            var curr = first
            while (curr <= last) {
                list.add(uIntToIp(curr))
                curr++
            }
            return list
        } else {
            // For subnets larger than /24 (e.g. /16 or /8), scan the local /24 block of the phone's IP
            val baseUInt = ipUInt and 0xFFFFFF00u
            val list = mutableListOf<String>()
            for (i in 1..254) {
                list.add(uIntToIp(baseUInt or i.toUInt()))
            }
            return list
        }
    }

    fun ipToUInt(ip: String): UInt {
        val parts = ip.split(".").map { it.trim().toUIntOrNull() ?: 0u }
        if (parts.size != 4) return 0u
        return (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
    }

    fun uIntToIp(value: UInt): String {
        return "${(value shr 24) and 0xFFu}.${(value shr 16) and 0xFFu}.${(value shr 8) and 0xFFu}.${value and 0xFFu}"
    }

    fun prefixToMask(prefix: Int): UInt {
        if (prefix <= 0) return 0u
        if (prefix >= 32) return 0xFFFFFFFFu
        return (0xFFFFFFFFu shl (32 - prefix))
    }
}
