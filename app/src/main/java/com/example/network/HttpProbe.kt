package com.example.network

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.regex.Pattern

data class ProbeResult(
    val isAlive: Boolean,
    val port: Int,
    val responseTimeMs: Long = 0,
    val serverBanner: String? = null,
    val httpTitle: String? = null,
    val macAddress: String? = null,
    val isEspCandidate: Boolean = false
)

object HttpProbe {

    private val TITLE_PATTERN = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE)

    /**
     * Probes an IP on a specific port.
     * 1. Performs TCP handshake with short timeout (250ms).
     * 2. If connected, sends HTTP GET / and analyzes response.
     */
    fun probeHost(ip: String, port: Int = 80, timeoutMs: Int = 280): ProbeResult {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.soTimeout = 700
            val socketAddress = InetSocketAddress(ip, port)
            socket.connect(socketAddress, timeoutMs)

            val rtt = (System.currentTimeMillis() - startTime).coerceAtLeast(1)

            // Send lightweight HTTP GET
            val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
            val getRequest = "GET / HTTP/1.1\r\n" +
                    "Host: $ip\r\n" +
                    "User-Agent: DeviceScanner/1.0\r\n" +
                    "Accept: text/html,application/json,*/*\r\n" +
                    "Connection: close\r\n\r\n"
            writer.write(getRequest)
            writer.flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
            val responseHeader = StringBuilder()
            val responseBody = StringBuilder()
            var line: String? = reader.readLine()
            var isHeader = true
            var linesRead = 0

            var serverHeader: String? = null

            while (line != null && linesRead < 40) {
                if (isHeader) {
                    if (line.isEmpty()) {
                        isHeader = false
                    } else {
                        responseHeader.append(line).append("\n")
                        if (line.startsWith("Server:", ignoreCase = true)) {
                            serverHeader = line.substringAfter(":").trim()
                        }
                    }
                } else {
                    responseBody.append(line).append(" ")
                }
                line = reader.readLine()
                linesRead++
            }

            val bodyText = responseBody.toString()
            val headerText = responseHeader.toString()
            val combined = "$headerText\n$bodyText"

            // Look for <title>
            var title: String? = null
            val matcher = TITLE_PATTERN.matcher(bodyText)
            if (matcher.find()) {
                title = matcher.group(1)?.trim()
            }

            // Check if MAC is in response
            val discoveredMac = ArpTableReader.extractMacFromResponse(combined)

            // Check if ESP signature is present in headers, title or body
            val isEsp = combined.contains("ESP32", ignoreCase = true) ||
                    combined.contains("ESP-32", ignoreCase = true) ||
                    combined.contains("Espressif", ignoreCase = true) ||
                    combined.contains("Arduino", ignoreCase = true) ||
                    combined.contains("Tasmota", ignoreCase = true) ||
                    combined.contains("ESPHome", ignoreCase = true) ||
                    combined.contains("WLED", ignoreCase = true) ||
                    (serverHeader?.contains("ESP", ignoreCase = true) == true) ||
                    (serverHeader?.contains("lwIP", ignoreCase = true) == true)

            return ProbeResult(
                isAlive = true,
                port = port,
                responseTimeMs = rtt,
                serverBanner = serverHeader,
                httpTitle = title,
                macAddress = discoveredMac,
                isEspCandidate = isEsp
            )
        } catch (e: Exception) {
            return ProbeResult(isAlive = false, port = port)
        } finally {
            try {
                socket?.close()
            } catch (ignored: Throwable) {}
        }
    }
}
