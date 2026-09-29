package com.example.data.model

data class ScanProgress(
    val isScanning: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val activeIp: String? = null,
    val foundCount: Int = 0,
    val statusMessage: String = ""
)
