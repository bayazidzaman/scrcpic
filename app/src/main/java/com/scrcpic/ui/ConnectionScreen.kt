package com.scrcpic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrcpic.R
import com.scrcpic.adb.AdbManager
import com.scrcpic.adb.AdbPairingManager
import com.scrcpic.adb.PairingState
import com.scrcpic.scrcpy.SessionState
import com.scrcpic.usb.UsbAdbManager
import kotlinx.coroutines.launch

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

@Composable
fun ConnectionScreen(
    sessionState: SessionState,
    usbAdbManager: UsbAdbManager,
    onConnect: (ip: String, port: Int, preset: StreamQualityPreset, turnScreenOff: Boolean, isUsb: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pairingManager = remember { AdbPairingManager(context) }
    val pairingState by pairingManager.state.collectAsState()

    var selectedMode by remember { mutableStateOf(ConnectionMode.WIFI) }
    var ipAddress by remember { mutableStateOf(AdbManager.DEFAULT_IP) }
    var portText by remember { mutableStateOf(AdbManager.DEFAULT_PORT.toString()) }
    var turnScreenOffOnConnect by remember { mutableStateOf(true) }
    var discoveredUsbDevice by remember { mutableStateOf<UsbAdbManager.DiscoveredAdbDevice?>(null) }

    var isSwitchingToWireless by remember { mutableStateOf(false) }
    var switchStatusMessage by remember { mutableStateOf<String?>(null) }
    var switchStatusIsError by remember { mutableStateOf(false) }

    var showPairingDialog by remember { mutableStateOf(false) }
    var pairingIp by remember { mutableStateOf(ipAddress) }
    var pairingPortText by remember { mutableStateOf("") }
    var pairingCodeText by remember { mutableStateOf("") }

    LaunchedEffect(selectedMode) {
        if (selectedMode == ConnectionMode.USB) {
            discoveredUsbDevice = usbAdbManager.findConnectedAdbDevice()
        }
    }

    val presets = remember {
        listOf(
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
    }
    var selectedPreset by remember { mutableStateOf(presets[0]) }
    val isConnecting = sessionState is SessionState.Connecting

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF080C14)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Ambient Radial Glow behind the header
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x2838BDF8),
                                Color(0x186366F1),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Brand Header with new 3D logo
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Scrcpic Logo",
                    modifier = Modifier
                        .size(76.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Scrcpic",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Surface(
                        color = Color(0x2638BDF8),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0x4D38BDF8))
                    ) {
                        Text(
                            text = "v4.1",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Connection Mode Selector: Wi-Fi vs USB Cable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x601E293B), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Wi-Fi Tab
                    Surface(
                        onClick = { if (!isConnecting) selectedMode = ConnectionMode.WIFI },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedMode == ConnectionMode.WIFI) Color(0xFF4F46E5) else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Wi-Fi Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }

                    // USB Cable Tab
                    Surface(
                        onClick = {
                            if (!isConnecting) {
                                selectedMode = ConnectionMode.USB
                                discoveredUsbDevice = usbAdbManager.findConnectedAdbDevice()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedMode == ConnectionMode.USB) Color(0xFF06B6D4) else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Usb, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("USB Cable", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Content: Wi-Fi Inputs OR USB Cable Card
                if (selectedMode == ConnectionMode.WIFI) {
                    // Wi-Fi IP & Port Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { ipAddress = it },
                            label = { Text("IP Address") },
                            leadingIcon = {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            },
                            singleLine = true,
                            enabled = !isConnecting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = Color(0xFF38BDF8),
                                unfocusedLabelColor = Color(0xFF94A3B8),
                                focusedContainerColor = Color(0x400F172A),
                                unfocusedContainerColor = Color(0x400F172A)
                            )
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it },
                            label = { Text("Port") },
                            leadingIcon = {
                                Icon(Icons.Default.SettingsEthernet, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = !isConnecting,
                            modifier = Modifier.width(115.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF818CF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = Color(0xFF818CF8),
                                unfocusedLabelColor = Color(0xFF94A3B8),
                                focusedContainerColor = Color(0x400F172A),
                                unfocusedContainerColor = Color(0x400F172A)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Wireless Pairing Assistant Shortcut
                    Surface(
                        onClick = {
                            pairingIp = ipAddress
                            pairingManager.resetState()
                            showPairingDialog = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0x2038BDF8),
                        border = BorderStroke(1.dp, Color(0x4038BDF8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚡", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Wireless Pairing Assistant (Android 11+)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Text(
                                        text = "পিসি ছাড়া 6-digit কোড দিয়ে ওয়্যারলেস কানেক্ট করুন",
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                            Text(
                                text = "Pair ❯",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                } else {
                    // USB Cable Device Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x60111827),
                        border = BorderStroke(1.dp, if (discoveredUsbDevice != null) Color(0xFF06B6D4) else Color(0x33334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Usb,
                                        contentDescription = null,
                                        tint = if (discoveredUsbDevice != null) Color(0xFF22D3EE) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (discoveredUsbDevice != null) {
                                            discoveredUsbDevice!!.device.productName ?: "USB ADB Device"
                                        } else {
                                            "No USB Device Detected"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = Color.White
                                    )
                                }

                                Surface(
                                    onClick = { discoveredUsbDevice = usbAdbManager.findConnectedAdbDevice() },
                                    shape = CircleShape,
                                    color = Color(0x2238BDF8),
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Scan", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (discoveredUsbDevice != null) {
                                if (discoveredUsbDevice!!.hasPermission) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ready to mirror via USB cable (Direct OTG)", fontSize = 11.5.sp, color = Color(0xFF34D399))
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Switch to Wireless 1-Touch Button
                                    Button(
                                        onClick = {
                                            isSwitchingToWireless = true
                                            switchStatusMessage = null
                                            coroutineScope.launch {
                                                val result = usbAdbManager.enableWirelessMode(5555)
                                                isSwitchingToWireless = false
                                                result.onSuccess { detectedIp ->
                                                    if (detectedIp.isNotBlank()) {
                                                        ipAddress = detectedIp
                                                    }
                                                    portText = "5555"
                                                    switchStatusIsError = false
                                                    switchStatusMessage = "Wireless 5555 সক্রিয় হয়েছে! এখন ক্যাবল খুলে Wi-Fi সিলেক্ট করে কানেক্ট দিন।"
                                                    selectedMode = ConnectionMode.WIFI
                                                }.onFailure { err ->
                                                    switchStatusIsError = true
                                                    switchStatusMessage = "ত্রুটি: ${err.message ?: "Wireless mode activation failed"}"
                                                }
                                            }
                                        },
                                        enabled = !isSwitchingToWireless && !isConnecting,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0D9488),
                                            disabledContainerColor = Color(0x330D9488)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                                    ) {
                                        if (isSwitchingToWireless) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Activating Port 5555...", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        } else {
                                            Text("⚡ Switch to Wireless (ক্যাবল খুলে চালান)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { usbAdbManager.requestPermission(discoveredUsbDevice!!.device) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(34.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                    ) {
                                        Text("Grant USB Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }
                            } else {
                                Text(
                                    text = "Connect broken phone using Type-C cable & enable USB Debugging",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }

                if (switchStatusMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (switchStatusIsError) Color(0x33EF4444) else Color(0x2610B981)
                        ),
                        border = BorderStroke(1.dp, if (switchStatusIsError) Color(0x66EF4444) else Color(0x4D10B981))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (switchStatusIsError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (switchStatusIsError) Color(0xFFF87171) else Color(0xFF34D399),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = switchStatusMessage!!,
                                color = if (switchStatusIsError) Color(0xFFFCA5A5) else Color(0xFFA7F3D0),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Streaming Quality Label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Quality Preset",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2x2 Grid of Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PresetTile(
                        preset = presets[0],
                        isSelected = selectedPreset == presets[0],
                        enabled = !isConnecting,
                        onClick = { selectedPreset = presets[0] },
                        modifier = Modifier.weight(1f)
                    )
                    PresetTile(
                        preset = presets[1],
                        isSelected = selectedPreset == presets[1],
                        enabled = !isConnecting,
                        onClick = { selectedPreset = presets[1] },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PresetTile(
                        preset = presets[2],
                        isSelected = selectedPreset == presets[2],
                        enabled = !isConnecting,
                        onClick = { selectedPreset = presets[2] },
                        modifier = Modifier.weight(1f)
                    )
                    PresetTile(
                        preset = presets[3],
                        isSelected = selectedPreset == presets[3],
                        enabled = !isConnecting,
                        onClick = { selectedPreset = presets[3] },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Minimal Target Screen Off Toggle (-S)
                Surface(
                    onClick = { if (!isConnecting) turnScreenOffOnConnect = !turnScreenOffOnConnect },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x60111827),
                    border = BorderStroke(1.dp, Color(0x22334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (turnScreenOffOnConnect) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = if (turnScreenOffOnConnect) Color(0xFF38BDF8) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "Target Screen Off (-S)",
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )

                        Switch(
                            checked = turnScreenOffOnConnect,
                            onCheckedChange = { if (!isConnecting) turnScreenOffOnConnect = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF4F46E5),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val isUsbMode = selectedMode == ConnectionMode.USB
                val canConnect = if (isUsbMode) {
                    discoveredUsbDevice != null && discoveredUsbDevice!!.hasPermission
                } else {
                    ipAddress.isNotBlank()
                }

                // Connect Button with Gradient
                Button(
                    onClick = {
                        val port = portText.toIntOrNull() ?: AdbManager.DEFAULT_PORT
                        onConnect(ipAddress.trim(), port, selectedPreset, turnScreenOffOnConnect, isUsbMode)
                    },
                    enabled = !isConnecting && canConnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .shadow(10.dp, RoundedCornerShape(14.dp), spotColor = if (isUsbMode) Color(0xFF06B6D4) else Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color(0x40334155)
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = if (!isConnecting && canConnect) {
                                    if (isUsbMode) {
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF6366F1))
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF06B6D4), Color(0xFF4F46E5), Color(0xFF7C3AED))
                                        )
                                    }
                                } else {
                                    Brush.linearGradient(
                                        listOf(Color(0xFF334155), Color(0xFF1E293B))
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isConnecting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Connecting...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isUsbMode) Icons.Default.Usb else Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isUsbMode) "Connect via USB Cable" else "Connect & Mirror (Wi-Fi)",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Error / Connecting Status Banner
                AnimatedVisibility(
                    visible = sessionState is SessionState.Connecting || sessionState is SessionState.Error,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    when (val state = sessionState) {
                        is SessionState.Connecting -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, Color(0x3338BDF8))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF38BDF8),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = state.step,
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        is SessionState.Error -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
                                border = BorderStroke(1.dp, Color(0x66EF4444))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Error",
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = state.message,
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        else -> {}
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showPairingDialog) {
        AlertDialog(
            onDismissRequest = {
                if (pairingState !is PairingState.Pairing) {
                    showPairingDialog = false
                }
            },
            containerColor = Color(0xFF0F172A),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE2E8F0),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Wireless Pairing (Android 11+)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "পিসির সাহায্য ছাড়া ফোনের ওয়্যারলেস ডিবাগিং চালু করতে:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "১. অপর ফোনে Developer options -> Wireless debugging চালু করুন।\n২. 'Pair device with pairing code' এ ট্যাপ করুন।\n৩. সেখানে দেখানো Port ও 6-digit Code নিচে বসান:",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pairingIp,
                        onValueChange = { pairingIp = it },
                        label = { Text("Device IP Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0x301E293B),
                            unfocusedContainerColor = Color(0x301E293B)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = pairingPortText,
                            onValueChange = { pairingPortText = it },
                            label = { Text("Pairing Port") },
                            placeholder = { Text("e.g. 38491", color = Color(0xFF64748B)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF818CF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0x301E293B),
                                unfocusedContainerColor = Color(0x301E293B)
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedTextField(
                            value = pairingCodeText,
                            onValueChange = { pairingCodeText = it },
                            label = { Text("6-Digit Code") },
                            placeholder = { Text("123456", color = Color(0xFF64748B)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF818CF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0x301E293B),
                                unfocusedContainerColor = Color(0x301E293B)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    when (val st = pairingState) {
                        is PairingState.Pairing -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x2238BDF8), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF38BDF8),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(st.message, fontSize = 11.5.sp, color = Color(0xFFE0F2FE))
                            }
                        }
                        is PairingState.Success -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x2210B981), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(st.message, fontSize = 11.5.sp, color = Color(0xFFA7F3D0))
                            }
                        }
                        is PairingState.Error -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x22EF4444), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(st.error, fontSize = 11.5.sp, color = Color(0xFFFCA5A5))
                            }
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                if (pairingState is PairingState.Success) {
                    Button(
                        onClick = {
                            ipAddress = pairingIp.trim()
                            portText = "5555"
                            showPairingDialog = false
                            pairingManager.resetState()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Connect Now", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            val pPort = pairingPortText.toIntOrNull() ?: 0
                            coroutineScope.launch {
                                pairingManager.pairAndActivate(
                                    targetIp = pairingIp.trim(),
                                    pairingPort = pPort,
                                    pairingCode = pairingCodeText.trim(),
                                    connectPort = pPort
                                )
                            }
                        },
                        enabled = pairingState !is PairingState.Pairing && pairingIp.isNotBlank() && pairingPortText.isNotBlank() && pairingCodeText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text("Pair Device", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPairingDialog = false
                        pairingManager.resetState()
                    },
                    enabled = pairingState !is PairingState.Pairing
                ) {
                    Text("Close", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

@Composable
private fun PresetTile(
    preset: StreamQualityPreset,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0x334F46E5) else Color(0x60111827),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) Color(0xFF6366F1) else Color(0x22334155)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = preset.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                )

                if (preset.badge.isNotEmpty()) {
                    Surface(
                        color = if (preset.isZeroLag) Color(0x2606B6D4) else Color(0x266366F1),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = preset.badge,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (preset.isZeroLag) Color(0xFF22D3EE) else Color(0xFFA5B4FC),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = preset.subtitle,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Normal,
                color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B)
            )
        }
    }
}
