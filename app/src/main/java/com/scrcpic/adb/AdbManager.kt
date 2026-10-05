package com.scrcpic.adb

import android.content.Context
import android.util.Log
import dadb.AdbKeyPair
import dadb.AdbShellStream
import dadb.AdbStream
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AdbManager(private val context: Context) {

    companion object {
        private const val TAG = "AdbManager"
        const val DEFAULT_IP = "192.168.0.145"
        const val DEFAULT_PORT = 5555
        const val REMOTE_SERVER_PATH = "/data/local/tmp/scrcpy-server.jar"
        const val ABSTRACT_SOCKET_NAME = "localabstract:scrcpy"
    }

    private var dadb: Dadb? = null
    private var shellStream: AdbShellStream? = null

    val isConnected: Boolean
        get() = dadb != null

    private fun getOrCreateKeyPair(): AdbKeyPair {
        val privKey = File(context.filesDir, "adbkey")
        val pubKey = File(context.filesDir, "adbkey.pub")
        if (!privKey.exists() || !pubKey.exists()) {
            Log.d(TAG, "Generating new ADB 2048-bit RSA key pair...")
            AdbKeyPair.generate(privKey, pubKey)
        }
        return AdbKeyPair.read(privKey, pubKey)
    }

    suspend fun connect(host: String, port: Int = DEFAULT_PORT): Dadb = withContext(Dispatchers.IO) {
        disconnect()
        val keyPair = getOrCreateKeyPair()
        Log.d(TAG, "Connecting to ADB target $host:$port...")
        val instance = Dadb.create(host, port, keyPair)
        dadb = instance
        instance
    }

    private var isServerPushed = false

    suspend fun pushServerAsset(): Unit = withContext(Dispatchers.IO) {
        if (isServerPushed) {
            Log.d(TAG, "scrcpy-server.jar already pushed to remote target, skipping file transfer.")
            return@withContext
        }
        val currentDadb = dadb ?: throw IllegalStateException("Not connected to ADB")
        
        // Extract asset to local cache file first
        val localJar = File(context.cacheDir, "scrcpy-server.jar")
        if (!localJar.exists() || localJar.length() == 0L) {
            context.assets.open("scrcpy-server.jar").use { input ->
                FileOutputStream(localJar).use { output ->
                    input.copyTo(output)
                }
            }
        }

        Log.d(TAG, "Pushing scrcpy-server.jar (${localJar.length()} bytes) to $REMOTE_SERVER_PATH...")
        // 0b111101101 is 0755 permissions in octal
        currentDadb.push(localJar, REMOTE_SERVER_PATH, 493, System.currentTimeMillis())
        isServerPushed = true
        Log.d(TAG, "scrcpy-server.jar successfully pushed to remote device.")
    }

    suspend fun startServer(
        version: String = "4.1",
        maxSize: Int = 1280,
        bitRate: Int = 8000000,
        maxFps: Int = 30
    ): AdbShellStream = withContext(Dispatchers.IO) {
        val currentDadb = dadb ?: throw IllegalStateException("Not connected to ADB")
        
        // Omitting scid allows the server to create the default socket name 'localabstract:scrcpy'
        // tunnel_forward=true: Server creates localabstract:scrcpy and waits for client connections
        // audio=false: Focus on video and touch responsiveness
        // control=true: Enable touch/keyboard injection
        // send_frame_meta=true: 12-byte packet headers (PTS + packet size)
        val cmd = "CLASSPATH=$REMOTE_SERVER_PATH app_process / com.genymobile.scrcpy.Server $version " +
                "tunnel_forward=true audio=false control=true send_frame_meta=true stay_awake=true " +
                "max_size=$maxSize video_bit_rate=$bitRate max_fps=$maxFps"

        Log.d(TAG, "Executing scrcpy-server shell command: $cmd")
        val stream = currentDadb.openShell(cmd)
        shellStream = stream
        stream
    }

    suspend fun openSocket(destination: String = ABSTRACT_SOCKET_NAME): AdbStream = withContext(Dispatchers.IO) {
        val currentDadb = dadb ?: throw IllegalStateException("Not connected to ADB")
        currentDadb.open(destination)
    }

    fun disconnect() {
        try {
            shellStream?.close()
        } catch (_: Exception) {}
        shellStream = null

        try {
            dadb?.close()
        } catch (_: Exception) {}
        dadb = null
    }
}
