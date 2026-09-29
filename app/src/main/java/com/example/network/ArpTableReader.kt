package com.example.network

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.regex.Pattern

object ArpTableReader {

    private val MAC_PATTERN = Pattern.compile("..:..:..:..:..:..")

    /**
     * Reads /proc/net/arp to look up hardware MAC address for a given IP.
     * Note: Access may be restricted on Android 10+ depending on SELinux policy,
     * so this operates with safe fallback and returns null if not accessible.
     */
    fun getMacForIp(targetIp: String): String? {
        try {
            val file = File("/proc/net/arp")
            if (!file.exists() || !file.canRead()) return null

            BufferedReader(FileReader(file)).use { reader ->
                var line: String? = reader.readLine() // skip header line
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line!!.split("\\s+".toRegex())
                    if (tokens.size >= 4) {
                        val ip = tokens[0]
                        val mac = tokens[3]
                        if (ip.equals(targetIp, ignoreCase = true) &&
                            MAC_PATTERN.matcher(mac).matches() &&
                            mac != "00:00:00:00:00:00"
                        ) {
                            return mac.uppercase()
                        }
                    }
                }
            }
        } catch (ignored: Throwable) {
            // Safe fallback if restricted by Android SELinux
        }
        return null
    }

    /**
     * Extracts MAC address from HTTP response header or JSON payload if present
     * (e.g. ESP32 /status response: {"mac":"24:0A:C4:XX:XX:XX"} or header X-ESP-MAC).
     */
    fun extractMacFromResponse(text: String): String? {
        val matcher = MAC_PATTERN.matcher(text)
        if (matcher.find()) {
            val candidate = matcher.group()
            if (candidate != "00:00:00:00:00:00") {
                return candidate.uppercase()
            }
        }
        return null
    }
}
