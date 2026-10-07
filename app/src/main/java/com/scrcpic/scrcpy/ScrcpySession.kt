package com.scrcpic.scrcpy

import android.content.Context
import android.util.Log
import android.view.Surface
import com.scrcpic.adb.AdbManager
import com.scrcpic.control.ScrcpyController
import com.scrcpic.video.CoordinateTransformer
import com.scrcpic.video.VideoDecoder
import dadb.AdbStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SessionState {
    object Idle : SessionState()
    data class Connecting(val step: String) : SessionState()
    data class Connected(val deviceName: String, val width: Int, val height: Int) : SessionState()
    data class Error(val message: String) : SessionState()
}

class ScrcpySession(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "ScrcpySession"
    }

    private val adbManager = AdbManager(context)
    val usbAdbManager = com.scrcpic.usb.UsbAdbManager(context)
    val coordinateTransformer = CoordinateTransformer()

    fun getDadbInstance() = adbManager.getDadb()

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    var controller: ScrcpyController? = null
        private set

    var scrcpyVideoWidth: Int = 1080
        private set
    var scrcpyVideoHeight: Int = 2400
        private set

    private var videoDecoder: VideoDecoder? = null
    private var videoStream: AdbStream? = null
    private var controlStream: AdbStream? = null

    private var connectJob: Job? = null
    private var lastHost: String = AdbManager.DEFAULT_IP
    private var lastPort: Int = AdbManager.DEFAULT_PORT
    private var lastMaxSize: Int = 1280
    private var lastBitRate: Int = 8000000
    private var lastMaxFps: Int = 30
    private var lastIsUsb: Boolean = false
    var currentSurface: Surface? = null
        private set

    private var sessionWakeLock: android.os.PowerManager.WakeLock? = null
    private var sessionWifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private var clipboardListener: android.content.ClipboardManager.OnPrimaryClipChangedListener? = null
    private var lastSyncedClipboard: String = ""

    private fun acquireLocks() {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            sessionWakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Scrcpic:SessionWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L)
            }
        } catch (_: Throwable) {}

        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            sessionWifiLock = wm?.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Scrcpic:SessionWifiLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Throwable) {}
    }

    private fun releaseLocks() {
        try {
            if (sessionWakeLock?.isHeld == true) sessionWakeLock?.release()
        } catch (_: Throwable) {}
        sessionWakeLock = null

        try {
            if (sessionWifiLock?.isHeld == true) sessionWifiLock?.release()
        } catch (_: Throwable) {}
        sessionWifiLock = null
    }

    fun connect(
        host: String,
        port: Int = AdbManager.DEFAULT_PORT,
        surface: Surface,
        maxSize: Int = 1280,
        bitRate: Int = 8000000,
        maxFps: Int = 30
    ) {
        lastHost = host
        lastPort = port
        lastMaxSize = maxSize
        lastBitRate = bitRate
        lastMaxFps = maxFps
        lastIsUsb = false
        currentSurface = surface

        acquireLocks()
        connectJob?.cancel()
        connectJob = coroutineScope.launch {
            try {
                disconnectInternal()
                connectInternal(host, port, surface, maxSize, bitRate, maxFps)
            } catch (e: Throwable) {
                if (!isActive) return@launch
                Log.e(TAG, "Connection failed", e)
                val msg = when {
                    e.message?.contains("Connection refused") == true ->
                        "Connection refused: Is Wi-Fi debugging active on $host:$port?"
                    e.message?.contains("device unauthorized") == true || e.message?.contains("auth") == true ->
                        "Device unauthorized: Please tap 'Allow' on the target phone display."
                    else -> e.message ?: "Unknown connection error"
                }
                _state.value = SessionState.Error(msg)
                disconnectInternal(clearError = false)
            }
        }
    }

    fun connectUsb(
        surface: Surface,
        maxSize: Int = 1280,
        bitRate: Int = 8000000,
        maxFps: Int = 30
    ) {
        lastMaxSize = maxSize
        lastBitRate = bitRate
        lastMaxFps = maxFps
        lastIsUsb = true
        currentSurface = surface

        acquireLocks()
        connectJob?.cancel()
        connectJob = coroutineScope.launch {
            try {
                disconnectInternal()
                _state.value = SessionState.Connecting("Detecting USB ADB device...")
                val usbDev = usbAdbManager.findConnectedAdbDevice()
                    ?: throw IllegalStateException("No USB device with ADB found. Connect via Type-C cable and enable USB Debugging.")

                _state.value = SessionState.Connecting("Starting USB bridge...")
                val bridge = usbAdbManager.startBridge(usbDev, coroutineScope)

                connectInternal("127.0.0.1", bridge.port, surface, maxSize, bitRate, maxFps)
            } catch (e: Throwable) {
                if (!isActive) return@launch
                Log.e(TAG, "USB Connection failed", e)
                val msg = when {
                    e.message?.contains("device unauthorized") == true || e.message?.contains("auth") == true ->
                        "Device unauthorized: Please tap 'Allow' on the target phone display."
                    else -> e.message ?: "USB connection error"
                }
                _state.value = SessionState.Error(msg)
                disconnectInternal(clearError = false)
            }
        }
    }

    fun reconnect(newSurface: Surface? = null) {
        val targetSurface = newSurface ?: currentSurface
        if (targetSurface == null || !targetSurface.isValid) {
            Log.w(TAG, "Cannot reconnect: Surface is null or invalid")
            return
        }
        if (lastIsUsb) {
            connectUsb(
                surface = targetSurface,
                maxSize = lastMaxSize,
                bitRate = lastBitRate,
                maxFps = lastMaxFps
            )
        } else {
            connect(
                host = lastHost,
                port = lastPort,
                surface = targetSurface,
                maxSize = lastMaxSize,
                bitRate = lastBitRate,
                maxFps = lastMaxFps
            )
        }
    }

    private suspend fun connectInternal(
        host: String,
        port: Int,
        surface: Surface,
        maxSize: Int,
        bitRate: Int,
        maxFps: Int
    ) {
        _state.value = SessionState.Connecting("Connecting to device via ADB ($host:$port)...")
        adbManager.connect(host, port)

        _state.value = SessionState.Connecting("Deploying scrcpy-server...")
        adbManager.pushServerAsset()

        _state.value = SessionState.Connecting("Launching scrcpy-server on target...")
        adbManager.startServer(
            maxSize = maxSize,
            bitRate = bitRate,
            maxFps = maxFps
        )

        // Give server process time to initialize its Unix domain socket
        _state.value = SessionState.Connecting("Establishing video tunnel...")
        var vStream: AdbStream? = null
        var attempts = 0
        val maxAttempts = 60
        while (vStream == null && attempts < maxAttempts) {
            try {
                delay(200)
                vStream = adbManager.openSocket()
            } catch (e: Exception) {
                attempts++
                Log.d(TAG, "Waiting for scrcpy video socket... attempt $attempts/$maxAttempts: ${e.message}")
            }
        }

        if (vStream == null) {
            throw IllegalStateException("Failed to connect to scrcpy video socket after $maxAttempts attempts")
        }
        videoStream = vStream

        _state.value = SessionState.Connecting("Establishing control tunnel...")
        var cStream: AdbStream? = null
        var cAttempts = 0
        val maxControlAttempts = 30
        while (cStream == null && cAttempts < maxControlAttempts) {
            try {
                delay(150)
                cStream = adbManager.openSocket()
            } catch (e: Exception) {
                cAttempts++
                Log.d(TAG, "Waiting for scrcpy control socket... attempt $cAttempts/$maxControlAttempts: ${e.message}")
            }
        }
        if (cStream == null) {
            throw IllegalStateException("Failed to connect to scrcpy control socket after $maxControlAttempts attempts")
        }
        controlStream = cStream

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val ctrl = ScrcpyController(cStream, coroutineScope)
        
        ctrl.onClipboardReceived = { text ->
            try {
                if (text != lastSyncedClipboard) {
                    lastSyncedClipboard = text
                    val clip = android.content.ClipData.newPlainText("Scrcpic", text)
                    clipboard.setPrimaryClip(clip)
                    Log.d(TAG, "Clipboard synced from remote: $text")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set local clipboard: ${e.message}")
            }
        }
        controller = ctrl
        
        clipboardListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
            try {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                    if (text.isNotEmpty() && text != lastSyncedClipboard) {
                        lastSyncedClipboard = text
                        controller?.setClipboard(text, paste = false)
                        Log.d(TAG, "Local clipboard auto-synced to remote")
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        clipboard.addPrimaryClipChangedListener(clipboardListener)

        _state.value = SessionState.Connecting("Initializing low-latency video decoder...")
        val decoder = VideoDecoder(
            videoStream = vStream,
            surface = surface,
            coroutineScope = coroutineScope,
            onConfigured = { deviceName, width, height ->
                scrcpyVideoWidth = width
                scrcpyVideoHeight = height
                coordinateTransformer.updateDimensions(
                    videoW = width,
                    videoH = height,
                    containerW = coordinateTransformer.currentContainerWidth,
                    containerH = coordinateTransformer.currentContainerHeight
                )
                _state.value = SessionState.Connected(deviceName, width, height)
            },
            onDimensionChanged = { width, height ->
                scrcpyVideoWidth = width
                scrcpyVideoHeight = height
                coordinateTransformer.updateDimensions(
                    videoW = width,
                    videoH = height,
                    containerW = coordinateTransformer.currentContainerWidth,
                    containerH = coordinateTransformer.currentContainerHeight
                )
                val currentState = _state.value
                if (currentState is SessionState.Connected) {
                    _state.value = currentState.copy(width = width, height = height)
                }
            },
            onFpsUpdate = { currentFps ->
                _fps.value = currentFps
            },
            onError = { throwable ->
                if (_state.value is SessionState.Connected || _state.value is SessionState.Connecting) {
                    val msg = throwable.message ?: ""
                    if (!msg.contains("Released", ignoreCase = true) && !msg.contains("closed", ignoreCase = true)) {
                        Log.w(TAG, "Video decoding error: $msg")
                        _state.value = SessionState.Error("Video decoding error: $msg")
                        val surf = currentSurface
                        if (surf != null && surf.isValid) {
                            coroutineScope.launch {
                                delay(1200)
                                if (_state.value is SessionState.Error) {
                                    Log.d(TAG, "Attempting automatic background reconnection...")
                                    reconnect(surf)
                                }
                            }
                        }
                    }
                }
            }
        )
        videoDecoder = decoder
        decoder.start()
    }

    fun isSessionAlive(): Boolean {
        return _state.value is SessionState.Connected && videoStream != null && videoDecoder != null
    }

    fun setOutputSurface(surface: Surface) {
        currentSurface = surface
        videoDecoder?.setOutputSurface(surface)
    }

    fun onSurfaceRestored(surface: Surface) {
        currentSurface = surface
        if (!isSessionAlive()) {
            Log.d(TAG, "onSurfaceRestored: Session was dropped while in background, auto-reconnecting...")
            reconnect(surface)
            return
        }
        Log.d(TAG, "onSurfaceRestored: Re-binding decoder to new surface instantly...")
        setOutputSurface(surface)
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Request a brand new IDR keyframe and session config to instantly recover video
                controller?.resetVideo()
                delay(100)
                controller?.sendWakeUp()
            } catch (e: Exception) {
                Log.w(TAG, "Error requesting wake up on restore: ${e.message}")
            }
        }
    }

    fun notifySurfaceDestroyed() {
        videoDecoder?.notifySurfaceDestroyed()
    }

    fun disconnect(clearError: Boolean = true) {
        connectJob?.cancel()
        connectJob = null
        disconnectInternal(clearError)
    }

    private fun disconnectInternal(clearError: Boolean = true) {
        if (clearError || _state.value !is SessionState.Error) {
            _state.value = SessionState.Idle
        }
        _fps.value = 0

        try {
            videoDecoder?.stop()
        } catch (_: Exception) {}
        videoDecoder = null

        try {
            videoStream?.close()
        } catch (_: Exception) {}
        videoStream = null

        try {
            controlStream?.close()
        } catch (_: Exception) {}
        controlStream = null

        controller = null
        
        clipboardListener?.let {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.removePrimaryClipChangedListener(it)
            } catch (e: Exception) {}
            clipboardListener = null
        }
        
        adbManager.disconnect()
        usbAdbManager.stopBridge()
        releaseLocks()
    }

    fun pushLocalClipboardToRemote() {
        val ctrl = controller ?: return
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString()
                if (!text.isNullOrEmpty() && text != lastSyncedClipboard) {
                    lastSyncedClipboard = text
                    ctrl.setClipboard(text, paste = false)
                    Log.d(TAG, "Local clipboard sent to remote on resume")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read local clipboard: ${e.message}")
        }
    }
}
