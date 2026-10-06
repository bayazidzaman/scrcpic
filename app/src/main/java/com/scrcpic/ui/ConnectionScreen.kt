package com.scrcpic.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrcpic.adb.AdbManager
import com.scrcpic.adb.AdbPairingManager
import com.scrcpic.scrcpy.SessionState
import com.scrcpic.usb.UsbAdbManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    val sharedPrefs = remember { context.getSharedPreferences("ScrcpicPrefs", android.content.Context.MODE_PRIVATE) }

    var savedIps by remember { 
        mutableStateOf(sharedPrefs.getStringSet("saved_ips", emptySet())?.toList() ?: emptyList())
    }

    var selectedMode by remember { mutableStateOf(ConnectionMode.WIFI) }
    var ipAddress by remember { 
        mutableStateOf(sharedPrefs.getString("last_used_ip", "")?.takeIf { it.isNotBlank() } ?: AdbManager.DEFAULT_IP)
    }
    var portText by remember { mutableStateOf(AdbManager.DEFAULT_PORT.toString()) }
    var turnScreenOffOnConnect by remember { mutableStateOf(true) }
    var discoveredUsbDevice by remember { mutableStateOf<UsbAdbManager.DiscoveredAdbDevice?>(null) }

    var isSwitchingToWireless by remember { mutableStateOf(false) }
    var switchStatusMessage by remember { mutableStateOf<String?>(null) }
    var switchStatusIsError by remember { mutableStateOf(false) }

    var showPairingDialog by remember { mutableStateOf(false) }

    // Check persistent pairing validation status
    var isCurrentDevicePaired by remember { mutableStateOf(false) }
    LaunchedEffect(ipAddress, showPairingDialog) {
        isCurrentDevicePaired = pairingManager.isDevicePaired(ipAddress)
    }

    LaunchedEffect(selectedMode) {
        if (selectedMode == ConnectionMode.USB) {
            discoveredUsbDevice = usbAdbManager.findConnectedAdbDevice()
        }
    }

    val presets = remember { DefaultQualityPresets }
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

                // Brand Header with 3D logo & version
                ConnectionHeader()

                Spacer(modifier = Modifier.height(14.dp))

                // Connection Mode Selector: Wi-Fi vs USB Cable
                ConnectionModeSelector(
                    selectedMode = selectedMode,
                    isConnecting = isConnecting,
                    onSelectMode = { mode ->
                        selectedMode = mode
                        if (mode == ConnectionMode.USB) {
                            discoveredUsbDevice = usbAdbManager.findConnectedAdbDevice()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Content: Wi-Fi Inputs OR USB Cable Card
                if (selectedMode == ConnectionMode.WIFI) {
                    // Wi-Fi IP & Port Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded && savedIps.isNotEmpty(),
                            onExpandedChange = { expanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = ipAddress,
                                onValueChange = { ipAddress = it },
                                label = { Text("IP Address") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Wifi,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                singleLine = true,
                                enabled = !isConnecting,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
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
                            ExposedDropdownMenu(
                                expanded = expanded && savedIps.isNotEmpty(),
                                onDismissRequest = { expanded = false }
                            ) {
                                savedIps.forEach { ip ->
                                    DropdownMenuItem(
                                        text = { Text(ip, color = Color.White) },
                                        onClick = {
                                            ipAddress = ip
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it },
                            label = { Text("Port") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.SettingsEthernet,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(18.dp)
                                )
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

                    // Wireless Pairing Validation Status & Assistant Chip
                    PairingStatusChip(
                        isPaired = isCurrentDevicePaired,
                        targetIp = ipAddress,
                        onClick = {
                            pairingManager.resetState()
                            showPairingDialog = true
                        }
                    )
                } else {
                    // USB Cable Device Card
                    UsbConnectionCard(
                        discoveredUsbDevice = discoveredUsbDevice,
                        isConnecting = isConnecting,
                        isSwitchingToWireless = isSwitchingToWireless,
                        switchStatusMessage = switchStatusMessage,
                        switchStatusIsError = switchStatusIsError,
                        onRefresh = { discoveredUsbDevice = usbAdbManager.findConnectedAdbDevice() },
                        onRequestPermission = { dev -> usbAdbManager.requestPermission(dev) },
                        onSwitchToWireless = {
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
                                    switchStatusMessage = "Port 5555 activated! Unplug cable and connect via Wi-Fi."
                                    selectedMode = ConnectionMode.WIFI
                                }.onFailure { err ->
                                    switchStatusIsError = true
                                    switchStatusMessage = "Error: ${err.message ?: "Wireless activation failed"}"
                                }
                            }
                        }
                    )
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

                // Target Screen Off Toggle (-S)
                ScreenOffToggle(
                    checked = turnScreenOffOnConnect,
                    enabled = !isConnecting,
                    onCheckedChange = { turnScreenOffOnConnect = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                val isUsbMode = selectedMode == ConnectionMode.USB
                val canConnect = if (isUsbMode) {
                    discoveredUsbDevice != null && discoveredUsbDevice!!.hasPermission
                } else {
                    ipAddress.isNotBlank()
                }

                // Connect Button with Gradient
                ConnectActionButton(
                    isConnecting = isConnecting,
                    isUsbMode = isUsbMode,
                    canConnect = canConnect,
                    onClick = {
                        val port = portText.toIntOrNull() ?: AdbManager.DEFAULT_PORT
                        val trimmedIp = ipAddress.trim()
                        
                        if (trimmedIp.isNotBlank() && !isUsbMode) {
                            val newSet = savedIps.toMutableSet().apply { add(trimmedIp) }
                            sharedPrefs.edit()
                                .putString("last_used_ip", trimmedIp)
                                .putStringSet("saved_ips", newSet)
                                .apply()
                            savedIps = newSet.toList()
                        }
                        
                        onConnect(trimmedIp, port, selectedPreset, turnScreenOffOnConnect, isUsbMode)
                    }
                )

                // Error / Connecting Status Banner
                ConnectionStatusBanner(sessionState = sessionState)

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showPairingDialog) {
        WirelessPairingDialog(
            pairingManager = pairingManager,
            initialIp = ipAddress,
            onDismiss = { showPairingDialog = false },
            onPairSuccess = { pairedIp ->
                ipAddress = pairedIp
                portText = "5555"
                isCurrentDevicePaired = true
                showPairingDialog = false
            }
        )
    }
}
