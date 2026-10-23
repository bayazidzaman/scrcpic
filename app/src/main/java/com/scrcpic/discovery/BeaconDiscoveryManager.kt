package com.scrcpic.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

data class DiscoveredBeaconDevice(
    val name: String,
    val ip: String,
    val port: Int = 5555,
    val latencyMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

data class NetworkStatusInfo(
    val ssid: String,
    val localIp: String,
    val isHotspotHost: Boolean,
    val subnet: String
)

class BeaconDiscoveryManager(private val context: Context) {

    companion object {
        private const val TAG = "BeaconDiscovery"
        const val BEACON_PORT = 45454
        const val QUERY_PAYLOAD = "SCRCPIC_BEACON_QUERY:v1"
        const val REPLY_PREFIX = "SCRCPIC_BEACON_REPLY:v1:"
    }

    fun getNetworkStatus(): NetworkStatusInfo {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return defaultStatus()
            for (intf in interfaces.asSequence()) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                if (name.contains("wlan") || name.contains("ap") || name.contains("swlan") || name.contains("eth")) {
                    for (addr in intf.inetAddresses.asSequence()) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val ip = addr.hostAddress ?: continue
                            val isHost = ip.endsWith(".1")
                            val subnet = ip.substringBeforeLast(".") + ".0/24"
                            val label = if (isHost) "Hotspot Host ($name)" else "Wi-Fi Network ($name)"
                            return NetworkStatusInfo(
                                ssid = label,
                                localIp = ip,
                                isHotspotHost = isHost,
                                subnet = subnet
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return defaultStatus()
    }

    private fun defaultStatus(): NetworkStatusInfo {
        return NetworkStatusInfo("Local Network", "127.0.0.1", false, "Local")
    }

    suspend fun searchBeacons(timeoutMs: Long = 2500): List<DiscoveredBeaconDevice> = withContext(Dispatchers.IO) {
        val discoveredList = mutableListOf<DiscoveredBeaconDevice>()
        val seenIps = mutableSetOf<String>()
        var socket: DatagramSocket? = null
        val startTime = System.currentTimeMillis()

        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 350
            }

            // Gather broadcast & target destinations
            val targets = getTargetAddresses()
            Log.d(TAG, "Broadcasting query to ${targets.size} targets: $targets")

            val queryBytes = QUERY_PAYLOAD.toByteArray(Charsets.UTF_8)
            for (dest in targets) {
                try {
                    val packet = DatagramPacket(queryBytes, queryBytes.size, dest, BEACON_PORT)
                    socket.send(packet)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send to $dest", e)
                }
            }

            // Listen for replies until timeout
            val buffer = ByteArray(1024)
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val recvPacket = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(recvPacket)
                    val latency = System.currentTimeMillis() - startTime
                    val rawMsg = String(recvPacket.data, 0, recvPacket.length).trim()
                    Log.d(TAG, "Received potential beacon reply: $rawMsg from ${recvPacket.address}")

                    if (rawMsg.startsWith(REPLY_PREFIX)) {
                        val jsonStr = rawMsg.substring(REPLY_PREFIX.length).trim()
                        val json = JSONObject(jsonStr)
                        val name = json.optString("name", "Unknown Remote Device")
                        val reportedIp = json.optString("ip", recvPacket.address.hostAddress ?: "")
                        val port = json.optInt("port", 5555)

                        val finalIp = if (reportedIp.isNotBlank() && reportedIp != "127.0.0.1") {
                            reportedIp
                        } else {
                            recvPacket.address.hostAddress ?: ""
                        }

                        if (finalIp.isNotBlank() && seenIps.add(finalIp)) {
                            discoveredList.add(
                                DiscoveredBeaconDevice(
                                    name = name,
                                    ip = finalIp,
                                    port = port,
                                    latencyMs = latency
                                )
                            )
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // Normal timeout per socket read, continue until total timeoutMs expires
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during beacon search", e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }

        discoveredList
    }

    private fun getTargetAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            // 1. Global broadcast
            list.add(InetAddress.getByName("255.255.255.255"))

            // 2. Subnet specific broadcasts & gateways
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return list
            for (intf in interfaces.asSequence()) {
                if (intf.isLoopback || !intf.isUp) continue
                for (interfaceAddress in intf.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null && broadcast is Inet4Address) {
                        list.add(broadcast)
                    }

                    val localAddr = interfaceAddress.address
                    if (localAddr is Inet4Address && !localAddr.isLoopbackAddress) {
                        val host = localAddr.hostAddress ?: continue
                        // If this device is hotspot host (.1), also send directly to common initial IPs (.2, .3, .4, .5)
                        if (host.endsWith(".1")) {
                            val prefix = host.substringBeforeLast(".")
                            for (lastOctet in 2..6) {
                                try {
                                    list.add(InetAddress.getByName("$prefix.$lastOctet"))
                                } catch (_: Exception) {}
                            }
                        } else {
                            // If this device is client, send directly to Gateway (.1)
                            val prefix = host.substringBeforeLast(".")
                            try {
                                list.add(InetAddress.getByName("$prefix.1"))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving targets", e)
        }
        return list.distinct()
    }
}
