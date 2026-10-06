<p align="center">
  <img src="app/src/main/res/drawable/app_logo.png" alt="Scrcpic Logo" width="180"/>
</p>

<h1 align="center">Scrcpic</h1>

<p align="center">
  <b>High-Performance Android-to-Android Screen Mirroring & Remote Control</b><br/>
  Control a broken phone, second device, or tablet directly from another Android phone over Wi-Fi or USB-C OTG cable — <i>No PC or Root Required</i>.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform"/>
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose"/>
  <img src="https://img.shields.io/badge/Protocol-Scrcpy%20v4.1-FF6F00?style=for-the-badge" alt="Scrcpy"/>
  <img src="https://img.shields.io/badge/License-Apache--2.0-blue?style=for-the-badge" alt="License"/>
</p>

<p align="center">
  <a href="https://github.com/bayazidzaman/scrcpic/releases/latest">
    <img src="https://img.shields.io/github/v/release/bayazidzaman/scrcpic?style=for-the-badge&color=22C55E&label=Latest%20Release" alt="Latest Release"/>
  </a>
  <a href="https://github.com/bayazidzaman/scrcpic/releases/download/v1.1.0/Scrcpic-v1.1.0-release.apk">
    <img src="https://img.shields.io/badge/Download-Latest%20APK-0EA5E9?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"/>
  </a>
</p>

---

## 🌟 Overview

**Scrcpic** is an open-source, ultra-low-latency remote control client for Android devices based on the battle-tested **Scrcpy protocol (v4.1)**. 

Unlike conventional screen sharing apps that require an intermediary computer, cloud servers, or root access, **Scrcpic runs entirely on your Android smartphone**. It connects directly to the target device via local Wi-Fi (ADB TCP/IP) or a direct USB-C to USB-C cable (ADB Host/OTG), deploys the lightweight `scrcpy-server` payload dynamically, and renders a smooth 60 FPS hardware-accelerated video stream with full multi-touch injection.

---

## ✨ Features

- ⚡ **Zero-Lag Hardware Video Pipeline:** Direct H.264/H.265 video decoding using Android's native `MediaCodec` and `SurfaceView` with low-latency flags.
- 🎮 **Full Remote Input Injection:** High-precision touch events (down, move, up), multi-touch gesture forwarding, and remote Android navigation buttons (Home, Back, Recents, Power, Wake-Up).
- 🔄 **Smart Auto-Recovery & Instant Reconnect:** Completely rock-solid background lifecycle! App minimizes or phone screen locks no longer cause black screens or frozen streams. A dedicated session reset mechanism forces an instant IDR frame generation upon return, guaranteeing a seamless resume every time.
- 📐 **Perfect Aspect Ratio Scaling:** The remote display always maintains its native proportions (no stretching or squashing) regardless of the screen size differences, and stays permanently locked in portrait format for optimal one-handed viewing.
- 🧠 **Smart Connection Memory:** Built-in IP address history with a convenient dropdown menu to quickly reconnect to all your previously used devices without having to re-type IPs.
- 🔌 **Dual Connection Modes:**
  - **Wi-Fi Mode:** Connect to any phone on your local network with Wi-Fi Debugging enabled (port 5555).
  - **USB Cable Mode (OTG):** Direct phone-to-phone connection using a standard USB-C to USB-C cable.
- 🔋 **Physical Display Off (Power Saver):** Mirror and use the remote phone while keeping its physical OLED/LCD screen turned off, saving battery and preventing heat build-up.
- 🎨 **Modern Obsidian Dark UI:** Built with Jetpack Compose and Material 3, featuring glassmorphism overlays, orientation toggles, and live FPS counters.
- 🚀 **No Root Required:** Operates strictly through standard Android Debug Bridge (ADB) protocols.
- 🛡️ **Self-Contained:** Automatically pushes and launches the bundled `scrcpy-server` v4.1 binary with session caching for instantaneous connection start-ups.

---

## 📱 How to Use

### Mode 1: Wireless Connection (Wi-Fi)

1. **On the Target Phone (the phone you want to mirror/control):**
   - Go to **Settings** > **Developer Options**.
   - Enable **USB Debugging** and **Wireless Debugging** (or connect once via PC to run `adb tcpip 5555`).
   - Find the phone's IP address (e.g. `192.168.0.145`).
2. **On the Controller Phone (running Scrcpic):**
   - Open **Scrcpic**.
   - Select **Wi-Fi** mode.
   - Enter the target phone's IP address and Port (`5555`).
   - Choose your preferred streaming preset (e.g., *Ultra Low-Lag 720p @ 60fps*).
   - Tap **Connect via Wi-Fi**.
   - If prompted on the target phone, tap **Allow USB Debugging**.

### Mode 2: Direct USB-C Cable (OTG)

1. Connect both phones with a USB-C to USB-C cable.
2. Ensure **USB Debugging** is turned ON on the target phone.
3. Open **Scrcpic** and tap **USB Cable** mode.
4. Tap **Connect via USB** — Scrcpic will auto-detect the connected ADB device, establish an in-memory USB bridge, and start mirroring immediately.

---

## ⚙️ Streaming Presets

| Preset | Resolution | Bitrate | Framerate | Best For |
| :--- | :--- | :--- | :--- | :--- |
| **Ultra Low-Lag** | 1280px (720p) | 8 Mbps | 60 FPS | Broken screen recovery, real-time gaming, fluid UI navigation |
| **Balanced** | 1600px | 12 Mbps | 60 FPS | General daily usage and productivity |
| **High Fidelity** | 1920px (1080p) | 16 Mbps | 60 FPS | Crystal clear text and high-res media viewing |

---

## 🛠️ Architecture

```
┌─────────────────────────────────┐                 ┌─────────────────────────────────┐
│     Controller Phone (Scrcpic)  │                 │    Target Phone (Remote Device) │
│                                 │                 │                                 │
│  ┌───────────────────────────┐  │                 │  ┌───────────────────────────┐  │
│  │   Jetpack Compose UI      │  │                 │  │       Android OS          │  │
│  │   (MirrorScreen, Controls)│  │                 │  │  (SurfaceFlinger / Display)│ │
│  └─────────────┬─────────────┘  │                 │  └─────────────▲─────────────┘  │
│                │ Touch / Keys   │                 │                │ Capture        │
│  ┌─────────────▼─────────────┐  │  ADB Control    │  ┌─────────────┴─────────────┐  │
│  │   ScrcpyController        ├──┼─────────────────┼─►│   scrcpy-server           │  │
│  │   (Protocol Serializer)   │  │  (Port 5555/USB)│  │   (InputManager)          │  │
│  └───────────────────────────┘  │                 │  └─────────────┬─────────────┘  │
│                                 │                 │                │ Encoded Stream │
│  ┌───────────────────────────┐  │  H.264 / H.265  │  ┌─────────────▼─────────────┐  │
│  │   VideoDecoder            │◄─┼─────────────────┼──┤   MediaCodec Encoder      │  │
│  │   (Hardware MediaCodec)   │  │  AdbStream Video│  │   (Screen Capture)        │  │
│  └─────────────┬─────────────┘  │                 │  └───────────────────────────┘  │
│                ▼ Render         │                 │                                 │
│  ┌───────────────────────────┐  │                 │                                 │
│  │   SurfaceView (Hardware)  │  │                 │                                 │
│  └───────────────────────────┘  │                 │                                 │
└─────────────────────────────────┘                 └─────────────────────────────────┘
```

---

## 📦 Building from Source

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17+
- Android SDK Platform 35 / Build Tools 35.0.0

### Build Steps
```bash
# Clone the repository
git clone https://github.com/bayazidzaman/scrcpic.git
cd scrcpic

# Compile unit tests
./gradlew test

# Build debug APK
./gradlew assembleDebug

# Output APK will be located at:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🤝 Acknowledgments & Credits

- **[scrcpy](https://github.com/Genymobile/scrcpy)** by Romain Vimont (Genymobile) — The foundation of modern open-source Android screen mirroring.
- **[dadb](https://github.com/mobile-dev-inc/dadb)** by mobile.dev — Fast, pure Kotlin implementation of the ADB client protocol.
- **[Jetpack Compose](https://developer.android.com/jetpack/compose)** — Android's modern toolkit for reactive UI.

---

## 📄 License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
