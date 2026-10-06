package com.scrcpic.ui

enum class ConnectionMode {
    WIFI,
    USB
}

data class StreamQualityPreset(
    val title: String,
    val subtitle: String,
    val badge: String = "",
    val maxSize: Int,
    val bitRate: Int,
    val maxFps: Int,
    val isZeroLag: Boolean = false
)

val DefaultQualityPresets = listOf(
    StreamQualityPreset(
        title = "720p • 60 FPS",
        subtitle = "Zero Lag • 4 Mbps",
        badge = "⚡ 60 FPS",
        maxSize = 720,
        bitRate = 4_000_000,
        maxFps = 60,
        isZeroLag = true
    ),
    StreamQualityPreset(
        title = "720p • 30 FPS",
        subtitle = "Lite • 2.5 Mbps",
        badge = "🔋 STABLE",
        maxSize = 720,
        bitRate = 2_500_000,
        maxFps = 30,
        isZeroLag = true
    ),
    StreamQualityPreset(
        title = "1080p • 30 FPS",
        subtitle = "Balanced • 6 Mbps",
        badge = "FHD",
        maxSize = 1080,
        bitRate = 6_000_000,
        maxFps = 30,
        isZeroLag = false
    ),
    StreamQualityPreset(
        title = "1080p • 60 FPS",
        subtitle = "Pro HD • 8 Mbps",
        badge = "PRO",
        maxSize = 1080,
        bitRate = 8_000_000,
        maxFps = 60,
        isZeroLag = false
    )
)
