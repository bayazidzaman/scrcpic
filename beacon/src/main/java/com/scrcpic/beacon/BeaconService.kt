package com.scrcpic.beacon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

class BeaconService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var socket: DatagramSocket? = null

    companion object {
        private const val TAG = "BeaconService"
        const val BEACON_PORT = 45454
        const val QUERY_PREFIX = "SCRCPIC_BEACON_QUERY"
        const val REPLY_PREFIX = "SCRCPIC_BEACON_REPLY:v1:"
        private const val NOTIFICATION_CHANNEL_ID = "scrcpic_beacon_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, BeaconService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        startBeaconListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Scrcpic Beacon",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Headless beacon for device discovery"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Scrcpic Beacon Active")
                .setContentText("Listening for discovery requests")
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Scrcpic Beacon Active")
                .setContentText("Listening for discovery requests")
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setOngoing(true)
                .build()
        }

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startBeaconListener() {
        serviceScope.launch {
            try {
                socket = DatagramSocket(BEACON_PORT).apply {
                    broadcast = true
                    reuseAddress = true
                }
                Log.i(TAG, "Beacon listener bound to UDP port $BEACON_PORT")

                val buffer = ByteArray(1024)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    val message = String(packet.data, 0, packet.length).trim()
                    Log.d(TAG, "Received UDP packet: '$message' from ${packet.address}:${packet.port}")

                    if (message.startsWith(QUERY_PREFIX)) {
                        handleQuery(packet.address, packet.port)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Beacon listener stopped or error", e)
            }
        }
    }

    private fun handleQuery(senderAddress: InetAddress, senderPort: Int) {
        try {
            val localIp = getLocalIpForSender(senderAddress)
            val modelName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val replyJson = "{\"name\":\"$modelName\",\"ip\":\"$localIp\",\"port\":5555}"
            val payload = "$REPLY_PREFIX$replyJson"
            val payloadBytes = payload.toByteArray(Charsets.UTF_8)

            val replyPacket = DatagramPacket(
                payloadBytes,
                payloadBytes.size,
                senderAddress,
                senderPort
            )
            socket?.send(replyPacket)
            Log.i(TAG, "Sent beacon response to $senderAddress:$senderPort -> $replyJson")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to respond to query", e)
        }
    }

    private fun getLocalIpForSender(senderAddress: InetAddress): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            var fallbackIp: String? = null

            for (intf in interfaces.asSequence()) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                val isWifiOrAp = name.contains("wlan") || name.contains("ap") || name.contains("eth") || name.contains("rndis")

                for (addr in intf.inetAddresses.asSequence()) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        // Check if in same /24 subnet
                        if (isSameSubnet24(addr.address, senderAddress.address)) {
                            return host
                        }
                        if (isWifiOrAp && fallbackIp == null) {
                            fallbackIp = host
                        }
                    }
                }
            }
            return fallbackIp ?: "127.0.0.1"
        } catch (e: Exception) {
            return "127.0.0.1"
        }
    }

    private fun isSameSubnet24(addr1: ByteArray, addr2: ByteArray): Boolean {
        if (addr1.size < 4 || addr2.size < 4) return false
        return addr1[0] == addr2[0] && addr1[1] == addr2[1] && addr1[2] == addr2[2]
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            socket?.close()
        } catch (_: Exception) {}
        serviceScope.cancel()
        Log.i(TAG, "BeaconService destroyed")
    }
}
