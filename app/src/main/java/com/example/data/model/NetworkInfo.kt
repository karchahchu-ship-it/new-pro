package com.example.data.model

data class NetworkInfo(
    val interfaceName: String,
    val ipAddress: String,
    val subnetMask: String,
    val networkAddress: String,
    val broadcastAddress: String,
    val prefixLength: Int,
    val cidr: String,
    val firstHostIp: String,
    val lastHostIp: String,
    val hostCount: Int,
    val isHotspot: Boolean,
    val isWifi: Boolean
)
