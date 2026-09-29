package com.example.network

import com.example.data.model.ScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

data class DiscoveredHost(
    val ip: String,
    val hostname: String?,
    val port: Int,
    val macAddress: String?,
    val serverBanner: String?,
    val httpTitle: String?,
    val responseTimeMs: Long
)

class NetworkScanner {

    private val concurrencyLimit = 25

    fun scanHosts(
        hosts: List<String>,
        onDeviceDiscovered: suspend (DiscoveredHost) -> Unit
    ): Flow<ScanProgress> = channelFlow {
        val total = hosts.size
        if (total == 0) {
            send(ScanProgress(isScanning = false, current = 0, total = 0, statusMessage = "Aucun hôte à analyser"))
            return@channelFlow
        }

        val completedCount = AtomicInteger(0)
        val discoveredCount = AtomicInteger(0)
        val semaphore = Semaphore(concurrencyLimit)

        send(
            ScanProgress(
                isScanning = true,
                current = 0,
                total = total,
                statusMessage = "Analyse du réseau..."
            )
        )

        coroutineScope {
            hosts.forEach { ip ->
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            // 1. Probe port 80
                            var probe = HttpProbe.probeHost(ip, port = 80, timeoutMs = 280)

                            // 2. If not alive on 80, try port 8080
                            if (!probe.isAlive) {
                                probe = HttpProbe.probeHost(ip, port = 8080, timeoutMs = 250)
                            }

                            if (probe.isAlive) {
                                val arpMac = ArpTableReader.getMacForIp(ip)
                                val finalMac = probe.macAddress ?: arpMac

                                // Attempt reverse DNS / mDNS hostname lookup
                                val resolvedHostname = runCatching {
                                    InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip }
                                        ?: InetAddress.getByName(ip).hostName.takeIf { it != ip }
                                }.getOrNull() ?: probe.httpTitle

                                val discovered = DiscoveredHost(
                                    ip = ip,
                                    hostname = resolvedHostname,
                                    port = probe.port,
                                    macAddress = finalMac,
                                    serverBanner = probe.serverBanner,
                                    httpTitle = probe.httpTitle,
                                    responseTimeMs = probe.responseTimeMs
                                )
                                discoveredCount.incrementAndGet()
                                onDeviceDiscovered(discovered)
                            }
                        } catch (ignored: Throwable) {
                        } finally {
                            val current = completedCount.incrementAndGet()
                            send(
                                ScanProgress(
                                    isScanning = true,
                                    current = current,
                                    total = total,
                                    activeIp = ip,
                                    foundCount = discoveredCount.get(),
                                    statusMessage = "Analyse du réseau..."
                                )
                            )
                        }
                    }
                }
            }
        }

        val finalFound = discoveredCount.get()
        send(
            ScanProgress(
                isScanning = false,
                current = total,
                total = total,
                foundCount = finalFound,
                statusMessage = "Scan terminé ($finalFound détectés)"
            )
        )
    }.flowOn(Dispatchers.Default)
}
