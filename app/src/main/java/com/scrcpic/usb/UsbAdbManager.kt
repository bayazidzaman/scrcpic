package com.scrcpic.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

class UsbAdbManager(private val context: Context) {

    companion object {
        private const val TAG = "UsbAdbManager"
        const val ACTION_USB_PERMISSION = "com.scrcpic.USB_PERMISSION"

        // Official Android ADB Interface Specification:
        // Class: 255 (0xFF - Vendor Specific), Subclass: 66 (0x42), Protocol: 1 (0x01)
        private const val ADB_CLASS = UsbConstants.USB_CLASS_VENDOR_SPEC
        private const val ADB_SUBCLASS = 0x42
        private const val ADB_PROTOCOL = 0x01
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var activeBridge: UsbAdbBridge? = null

    data class DiscoveredAdbDevice(
        val device: UsbDevice,
        val usbInterface: UsbInterface,
        val endpointIn: UsbEndpoint,
        val endpointOut: UsbEndpoint,
        val hasPermission: Boolean
    )

    fun findConnectedAdbDevice(): DiscoveredAdbDevice? {
        val deviceList = usbManager.deviceList
        for ((_, device) in deviceList) {
            val count = device.interfaceCount
            for (i in 0 until count) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == ADB_CLASS &&
                    iface.interfaceSubclass == ADB_SUBCLASS &&
                    iface.interfaceProtocol == ADB_PROTOCOL
                ) {
                    var epIn: UsbEndpoint? = null
                    var epOut: UsbEndpoint? = null
                    for (j in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(j)
                        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (ep.direction == UsbConstants.USB_DIR_IN) {
                                epIn = ep
                            } else {
                                epOut = ep
                            }
                        }
                    }
                    if (epIn != null && epOut != null) {
                        return DiscoveredAdbDevice(
                            device = device,
                            usbInterface = iface,
                            endpointIn = epIn,
                            endpointOut = epOut,
                            hasPermission = usbManager.hasPermission(device)
                        )
                    }
                }
            }
        }
        return null
    }

    fun requestPermission(device: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
        usbManager.requestPermission(device, pendingIntent)
    }

    fun startBridge(discovered: DiscoveredAdbDevice, coroutineScope: CoroutineScope): UsbAdbBridge {
        stopBridge()

        val connection = usbManager.openDevice(discovered.device)
            ?: throw IOException("Failed to open USB device connection. Please verify OTG / USB permission.")

        if (!connection.claimInterface(discovered.usbInterface, true)) {
            connection.close()
            throw IOException("Failed to claim ADB USB interface.")
        }

        val bridge = UsbAdbBridge(
            connection = connection,
            usbInterface = discovered.usbInterface,
            endpointIn = discovered.endpointIn,
            endpointOut = discovered.endpointOut,
            coroutineScope = coroutineScope
        )
        bridge.start()
        activeBridge = bridge
        return bridge
    }

    fun stopBridge() {
        try {
            activeBridge?.close()
        } catch (_: Exception) {}
        activeBridge = null
    }

    suspend fun enableWirelessMode(targetPort: Int = 5555): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val discovered = findConnectedAdbDevice()
            ?: return@withContext Result.failure(IllegalStateException("No USB device found. Connect device via USB/OTG cable first."))
        if (!usbManager.hasPermission(discovered.device)) {
            requestPermission(discovered.device)
            return@withContext Result.failure(IllegalStateException("USB permission required. Tap Allow on the USB prompt and try again."))
        }

        val tempScope = CoroutineScope(Dispatchers.IO)
        var bridge: UsbAdbBridge? = null
        try {
            bridge = startBridge(discovered, tempScope)
            val keyPair = com.scrcpic.adb.AdbManager(context).getOrCreateKeyPair()
            val dadb = dadb.Dadb.create("127.0.0.1", bridge.port, keyPair)

            // 1. Query target device's Wi-Fi IP address
            var ip = ""
            try {
                val ipResp = dadb.shell("ip -f inet addr show wlan0")
                val match = Regex("""inet\s+([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)""").find(ipResp.output)
                if (match != null) {
                    ip = match.groupValues[1]
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to query wlan0 IP: ${t.message}")
            }

            // 2. Open tcpip service to restart adbd in TCP mode on port 5555
            try {
                dadb.open("tcpip:$targetPort").close()
            } catch (t: Throwable) {
                dadb.shell("setprop service.adb.tcp.port $targetPort; stop adbd; start adbd")
            }

            dadb.close()
            Result.success(ip)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to enable wireless mode over USB", e)
            Result.failure(e)
        } finally {
            bridge?.close()
            stopBridge()
        }
    }

    class UsbAdbBridge(
        private val connection: UsbDeviceConnection,
        private val usbInterface: UsbInterface,
        private val endpointIn: UsbEndpoint,
        private val endpointOut: UsbEndpoint,
        private val coroutineScope: CoroutineScope
    ) : Closeable {

        private var serverSocket: ServerSocket? = null
        private var clientSocket: Socket? = null
        private var serverJob: Job? = null
        private var toUsbJob: Job? = null
        private var fromUsbJob: Job? = null

        @Volatile
        var isRunning = false
            private set

        val port: Int
            get() = serverSocket?.localPort ?: throw IllegalStateException("Bridge server socket is not active")

        fun start() {
            if (isRunning) return
            isRunning = true

            // Bind to dynamic local port on loopback interface
            val ss = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
            serverSocket = ss
            Log.d(TAG, "USB ADB bridge listening on 127.0.0.1:${ss.localPort}")

            serverJob = coroutineScope.launch(Dispatchers.IO) {
                try {
                    while (isRunning && isActive) {
                        val socket = ss.accept()
                        clientSocket?.close()
                        clientSocket = socket
                        startPumps(socket)
                    }
                } catch (_: Exception) {}
            }
        }

        private fun startPumps(socket: Socket) {
            toUsbJob?.cancel()
            fromUsbJob?.cancel()

            // 1. Socket -> USB bulk OUT pump
            toUsbJob = coroutineScope.launch(Dispatchers.IO) {
                val dis = DataInputStream(socket.getInputStream())
                val header = ByteArray(24)
                try {
                    while (isRunning && isActive) {
                        dis.readFully(header)

                        // Parse dataLength at bytes 12..15 (little endian)
                        val dataLength = (header[12].toInt() and 0xFF) or
                                ((header[13].toInt() and 0xFF) shl 8) or
                                ((header[14].toInt() and 0xFF) shl 16) or
                                ((header[15].toInt() and 0xFF) shl 24)

                        // 1. Send 24-byte header packet
                        val resHdr = connection.bulkTransfer(endpointOut, header, 24, 5000)
                        if (resHdr < 0) {
                            Log.w(TAG, "USB bulkTransfer header failed: $resHdr")
                            break
                        }

                        // 2. Send payload packet if non-empty
                        if (dataLength > 0) {
                            val payload = ByteArray(dataLength)
                            dis.readFully(payload)
                            val resPayload = connection.bulkTransfer(endpointOut, payload, dataLength, 10000)
                            if (resPayload < 0) {
                                Log.w(TAG, "USB bulkTransfer payload failed: $resPayload")
                                break
                            }

                            // Zero-length packet on exact packet size boundary
                            val maxPacket = endpointOut.maxPacketSize
                            if (maxPacket > 0 && dataLength % maxPacket == 0) {
                                connection.bulkTransfer(endpointOut, ByteArray(0), 0, 1000)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning && isActive) {
                        Log.d(TAG, "Socket to USB pump ended: ${e.message}")
                    }
                }
            }

            // 2. USB bulk IN -> Socket pump
            fromUsbJob = coroutineScope.launch(Dispatchers.IO) {
                val os = socket.getOutputStream()
                val buf = ByteArray(32 * 1024)
                try {
                    while (isRunning && isActive) {
                        val readBytes = connection.bulkTransfer(endpointIn, buf, buf.size, 5000)
                        if (readBytes > 0) {
                            os.write(buf, 0, readBytes)
                            os.flush()
                        } else if (readBytes < 0) {
                            // Timeout (returns -1) is normal when device is idle; keep pumping while running
                            if (!isRunning || !isActive) break
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning && isActive) {
                        Log.d(TAG, "USB to Socket pump ended: ${e.message}")
                    }
                }
            }
        }

        override fun close() {
            isRunning = false
            serverJob?.cancel()
            toUsbJob?.cancel()
            fromUsbJob?.cancel()

            try {
                clientSocket?.close()
            } catch (_: Exception) {}
            clientSocket = null

            try {
                serverSocket?.close()
            } catch (_: Exception) {}
            serverSocket = null

            try {
                connection.releaseInterface(usbInterface)
            } catch (_: Exception) {}

            try {
                connection.close()
            } catch (_: Exception) {}

            Log.d(TAG, "USB ADB bridge closed cleanly")
        }
    }
}
