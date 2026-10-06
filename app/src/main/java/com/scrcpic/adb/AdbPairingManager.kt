package com.scrcpic.adb

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

sealed class PairingState {
    object Idle : PairingState()
    data class Pairing(val message: String) : PairingState()
    data class Success(val ip: String, val message: String) : PairingState()
    data class Error(val error: String) : PairingState()
}

class AdbPairingManager(private val context: Context) {

    companion object {
        private const val TAG = "AdbPairingManager"
        private const val KEY_FILE = "scrcpic_adb_rsa.key"
        private const val CERT_FILE = "scrcpic_adb_cert.crt"
        private const val PREFS_NAME = "scrcpic_pairing_prefs"
        private const val KEY_PAIRED_DEVICES = "paired_devices"
        private const val KEY_LAST_PAIRED_IP = "last_paired_ip"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    fun isDevicePaired(targetIp: String): Boolean {
        if (targetIp.isBlank()) return false
        val pairedSet = prefs.getStringSet(KEY_PAIRED_DEVICES, emptySet()) ?: emptySet()
        return pairedSet.contains(targetIp.trim())
    }

    fun markDevicePaired(targetIp: String) {
        if (targetIp.isBlank()) return
        val currentSet = prefs.getStringSet(KEY_PAIRED_DEVICES, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(targetIp.trim())
        prefs.edit()
            .putStringSet(KEY_PAIRED_DEVICES, currentSet)
            .putString(KEY_LAST_PAIRED_IP, targetIp.trim())
            .apply()
    }

    fun unpairDevice(targetIp: String) {
        val currentSet = prefs.getStringSet(KEY_PAIRED_DEVICES, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.remove(targetIp.trim())
        prefs.edit().putStringSet(KEY_PAIRED_DEVICES, currentSet).apply()
    }

    fun getLastPairedIp(): String? {
        return prefs.getString(KEY_LAST_PAIRED_IP, null)
    }

    fun resetState() {
        _state.value = PairingState.Idle
    }

    private fun resetSslUtilsCache() {
        try {
            val sslUtilsClass = Class.forName("io.github.muntashirakon.adb.SslUtils")
            val field = sslUtilsClass.getDeclaredField("sslContext")
            field.isAccessible = true
            field.set(null, null)
            Log.d(TAG, "Reset libadb SslUtils cached sslContext successfully.")
        } catch (e: Throwable) {
            Log.w(TAG, "Could not reset SslUtils sslContext: ${e.message}")
        }
    }

    @Synchronized
    fun getOrCreateKeyAndCert(): Pair<PrivateKey, Certificate> {
        val keyFile = File(context.filesDir, KEY_FILE)
        val certFile = File(context.filesDir, CERT_FILE)

        if (keyFile.exists() && certFile.exists()) {
            try {
                val keyBytes = keyFile.readBytes()
                val certBytes = certFile.readBytes()
                val kf = KeyFactory.getInstance("RSA")
                val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(keyBytes))
                val cf = CertificateFactory.getInstance("X.509")
                val cert = cf.generateCertificate(ByteArrayInputStream(certBytes))
                Log.d(TAG, "Loaded existing software RSA key and certificate.")
                return Pair(privKey, cert)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to load cached key/cert, regenerating: ${t.message}")
                keyFile.delete()
                certFile.delete()
            }
        }

        Log.d(TAG, "Generating new software RSA key and self-signed X509 certificate...")
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val keyPair = kpg.generateKeyPair()

        val subject = X500Name("CN=Scrcpic")
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date(System.currentTimeMillis() - 86400000L)
        val notAfter = Date(System.currentTimeMillis() + 30L * 365 * 86400000L)

        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        val cert = JcaX509CertificateConverter().getCertificate(certHolder)

        try {
            keyFile.writeBytes(keyPair.private.encoded)
            certFile.writeBytes(cert.encoded)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to cache key/cert to storage: ${t.message}")
        }

        return Pair(keyPair.private, cert)
    }

    suspend fun pairAndActivate(
        targetIp: String,
        pairingPort: Int,
        pairingCode: String,
        connectPort: Int = 0
    ): Result<String> = withContext(Dispatchers.IO) {
        _state.value = PairingState.Pairing("Connecting to pairing service ($targetIp:$pairingPort)...")
        try {
            if (targetIp.isBlank() || pairingPort <= 0 || pairingCode.isBlank()) {
                val err = "Please enter valid IP, Pairing Port, and 6-digit Code."
                _state.value = PairingState.Error(err)
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            Log.d(TAG, "Initializing in-process SPAKE2+ TLS pairing with $targetIp:$pairingPort...")
            val (privKey, cert) = getOrCreateKeyAndCert()
            resetSslUtilsCache()

            val connectionManager = object : AbsAdbConnectionManager() {
                override fun getPrivateKey(): PrivateKey = privKey
                override fun getCertificate(): Certificate = cert
                override fun getDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            }
            connectionManager.setApi(Build.VERSION.SDK_INT)
            connectionManager.setHostAddress(targetIp)

            _state.value = PairingState.Pairing("Performing SPAKE2+ key exchange...")
            val paired = connectionManager.pair(targetIp, pairingPort, pairingCode.trim())

            if (paired) {
                Log.d(TAG, "Pairing succeeded! Attempting to activate Port 5555...")
                _state.value = PairingState.Pairing("Pairing successful! Activating port 5555...")

                // If working/connect port provided, connect to it and run tcpip:5555
                val workingPort = if (connectPort > 0) connectPort else 5555
                if (workingPort != 5555) {
                    try {
                        connectionManager.connect(targetIp, workingPort)
                        if (connectionManager.isConnected) {
                            val stream = connectionManager.openStream("tcpip:5555")
                            stream.close()
                            Log.d(TAG, "Successfully activated port 5555 over paired connection!")
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Could not automatically switch to port 5555: ${t.message}")
                    } finally {
                        try { connectionManager.disconnect() } catch (_: Throwable) {}
                    }
                }

                val successMsg = "Successfully paired with $targetIp!"
                markDevicePaired(targetIp)
                _state.value = PairingState.Success(targetIp, successMsg)
                Result.success(targetIp)
            } else {
                val errMsg = "Pairing failed. Make sure pairing code and port are correct."
                _state.value = PairingState.Error(errMsg)
                Result.failure(Exception(errMsg))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Pairing exception", e)
            val msgStr = e.message ?: e.toString()
            val errMsg = when {
                msgStr.contains("Exchanging message wasn't successful", ignoreCase = true) ->
                    "Invalid pairing code. Please recheck the 6-digit PIN."
                msgStr.contains("ECONNREFUSED", ignoreCase = true) || msgStr.contains("failed to connect", ignoreCase = true) ->
                    "Connection refused ($pairingPort). Ensure pairing dialog is active."
                else -> "Pairing failed [${e.javaClass.simpleName}]: $msgStr"
            }
            _state.value = PairingState.Error(errMsg)
            Result.failure(Exception(errMsg, e))
        }
    }
}
