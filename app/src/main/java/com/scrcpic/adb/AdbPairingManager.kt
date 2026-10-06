package com.scrcpic.adb

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

sealed class PairingState {
    object Idle : PairingState()
    data class Pairing(val message: String) : PairingState()
    data class Success(val ip: String, val message: String) : PairingState()
    data class Error(val error: String) : PairingState()
}

class AdbPairingManager(private val context: Context) {

    companion object {
        private const val TAG = "AdbPairingManager"
    }

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    fun resetState() {
        _state.value = PairingState.Idle
    }

    suspend fun pairAndActivate(
        targetIp: String,
        pairingPort: Int,
        pairingCode: String,
        connectPort: Int = pairingPort
    ): Result<String> = withContext(Dispatchers.IO) {
        _state.value = PairingState.Pairing("Connecting to pairing service ($targetIp:$pairingPort)...")
        try {
            if (targetIp.isBlank() || pairingPort <= 0 || pairingCode.isBlank()) {
                val err = "Please enter valid IP, Pairing Port, and 6-digit Code."
                _state.value = PairingState.Error(err)
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            val nativeAdb = File(context.applicationInfo.nativeLibraryDir, "libadb.so")
            val executable = if (nativeAdb.exists() && nativeAdb.canExecute()) {
                nativeAdb.absolutePath
            } else {
                "adb"
            }

            Log.d(TAG, "Starting pairing via $executable with $targetIp:$pairingPort...")
            val pb = ProcessBuilder(
                executable,
                "pair",
                "$targetIp:$pairingPort",
                pairingCode.trim()
            )
            pb.environment()["HOME"] = context.filesDir.absolutePath
            pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
            pb.redirectErrorStream(true)

            val proc = pb.start()
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
                Log.d(TAG, "[adb pair] $line")
            }
            val exitCode = proc.waitFor()

            val resultStr = output.toString()
            if (exitCode == 0 || resultStr.contains("Successfully paired", ignoreCase = true)) {
                _state.value = PairingState.Pairing("Pairing successful! Enabling TCP/IP mode...")

                try {
                    val connectPb = ProcessBuilder(
                        executable,
                        "connect",
                        "$targetIp:$connectPort"
                    )
                    connectPb.environment()["HOME"] = context.filesDir.absolutePath
                    connectPb.start().waitFor()

                    val tcpipPb = ProcessBuilder(
                        executable,
                        "-s",
                        "$targetIp:$connectPort",
                        "tcpip",
                        "5555"
                    )
                    tcpipPb.environment()["HOME"] = context.filesDir.absolutePath
                    tcpipPb.start().waitFor()
                } catch (t: Throwable) {
                    Log.w(TAG, "Post-pair tcpip activation fallback: ${t.message}")
                }

                val successMsg = "Successfully paired with $targetIp! Wireless debugging is now active."
                _state.value = PairingState.Success(targetIp, successMsg)
                Result.success(targetIp)
            } else {
                val errMsg = when {
                    resultStr.contains("failed to connect", ignoreCase = true) ->
                        "Failed to connect to pairing port $pairingPort. Ensure the 'Pair device' dialog is open on the target phone."
                    resultStr.contains("wrong password", ignoreCase = true) || resultStr.contains("auth", ignoreCase = true) ->
                        "Invalid pairing code. Please check the 6-digit code and try again."
                    resultStr.isNotBlank() -> resultStr.trim()
                    else -> "Pairing failed. Make sure both phones are on the same Wi-Fi network."
                }
                _state.value = PairingState.Error(errMsg)
                Result.failure(Exception(errMsg))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Pairing exception", e)
            val err = e.message ?: "Pairing service unavailable"
            _state.value = PairingState.Error(err)
            Result.failure(e)
        }
    }
}
