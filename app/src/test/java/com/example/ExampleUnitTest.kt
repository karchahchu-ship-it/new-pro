package com.example

import com.example.data.local.DeviceEntity
import com.example.network.NetworkScanner
import com.example.network.NetworkUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testSubnetCalculations() {
        // Prefix 24 -> 255.255.255.0
        val mask24 = NetworkUtils.prefixToMask(24)
        assertEquals("255.255.255.0", NetworkUtils.uIntToIp(mask24))

        // Prefix 16 -> 255.255.0.0
        val mask16 = NetworkUtils.prefixToMask(16)
        assertEquals("255.255.0.0", NetworkUtils.uIntToIp(mask16))

        // Prefix 28 -> 255.255.255.240
        val mask28 = NetworkUtils.prefixToMask(28)
        assertEquals("255.255.255.240", NetworkUtils.uIntToIp(mask28))
    }

    @Test
    fun testIpConversion() {
        val ip = "192.168.43.1"
        val uint = NetworkUtils.ipToUInt(ip)
        val converted = NetworkUtils.uIntToIp(uint)
        assertEquals(ip, converted)
    }

    @Test
    fun testDynamicSubnetAddressCalculation() {
        val ip = "192.168.50.45"
        val prefix = 24
        val maskUInt = NetworkUtils.prefixToMask(prefix)
        val ipUInt = NetworkUtils.ipToUInt(ip)
        val networkUInt = ipUInt and maskUInt
        val broadcastUInt = networkUInt or maskUInt.inv()

        assertEquals("192.168.50.0", NetworkUtils.uIntToIp(networkUInt))
        assertEquals("192.168.50.255", NetworkUtils.uIntToIp(broadcastUInt))

        val firstHost = NetworkUtils.uIntToIp(networkUInt + 1u)
        val lastHost = NetworkUtils.uIntToIp(broadcastUInt - 1u)
        assertEquals("192.168.50.1", firstHost)
        assertEquals("192.168.50.254", lastHost)
    }

    @Test
    fun testDeviceDisplayHostname() {
        val deviceWithHostname = DeviceEntity(
            macAddress = "AA:BB:CC:DD:EE:01",
            hostname = "kitchen-controller.local",
            lastIp = "192.168.43.50"
        )
        assertEquals("kitchen-controller.local", deviceWithHostname.displayHostname)

        val deviceWithoutHostname = DeviceEntity(
            macAddress = "AA:BB:CC:DD:EE:02",
            hostname = null,
            httpTitle = "Serveur Chauffage",
            lastIp = "192.168.43.51"
        )
        assertEquals("Serveur Chauffage", deviceWithoutHostname.displayHostname)

        val fallbackDevice = DeviceEntity(
            macAddress = "AA:BB:CC:DD:EE:03",
            hostname = null,
            httpTitle = null,
            lastIp = "192.168.43.52"
        )
        assertEquals("host-192-168-43-52.local", fallbackDevice.displayHostname)
    }

    @Test
    fun testScannerRunsWithoutFlowInvariantCrash() = runBlocking {
        val scanner = NetworkScanner()
        val hosts = listOf("127.0.0.1", "127.0.0.2")
        val progressUpdates = mutableListOf<com.example.data.model.ScanProgress>()
        scanner.scanHosts(hosts) {}.collect {
            progressUpdates.add(it)
        }
        assertTrue(progressUpdates.isNotEmpty())
    }
}
