package com.scrcpic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrcpic.adb.AdbPairingManager
import com.scrcpic.adb.PairingState
import kotlinx.coroutines.launch

@Composable
fun WirelessPairingDialog(
    pairingManager: AdbPairingManager,
    initialIp: String,
    onDismiss: () -> Unit,
    onPairSuccess: (pairedIp: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val pairingState by pairingManager.state.collectAsState()

    var pairingIp by remember { mutableStateOf(initialIp) }
    var pairingPortText by remember { mutableStateOf("") }
    var pairingCodeText by remember { mutableStateOf("") }
    var wirelessPortText by remember { mutableStateOf("") }

    val isPairing = pairingState is PairingState.Pairing

    AlertDialog(
        onDismissRequest = {
            if (!isPairing) {
                pairingManager.resetState()
                onDismiss()
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
                    text = "1. Enable 'Wireless debugging' on the target phone.\n2. Tap 'Pair device with pairing code'.\n3. Enter the pairing port, 6-digit PIN, and main wireless port:",
                    fontSize = 11.5.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pairingIp,
                    onValueChange = { pairingIp = it },
                    label = { Text("Device IP Address") },
                    singleLine = true,
                    enabled = !isPairing,
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
                        placeholder = { Text("e.g. 41635", color = Color(0xFF64748B)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !isPairing,
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
                        placeholder = { Text("623902", color = Color(0xFF64748B)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !isPairing,
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

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = wirelessPortText,
                    onValueChange = { wirelessPortText = it },
                    label = { Text("Wireless Port (Main Screen)") },
                    placeholder = { Text("e.g. 44001", color = Color(0xFF64748B)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !isPairing,
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
                        val pairedIp = pairingIp.trim()
                        pairingManager.resetState()
                        onPairSuccess(pairedIp)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Connect Now", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        val pPort = pairingPortText.toIntOrNull() ?: 0
                        val wPort = wirelessPortText.toIntOrNull() ?: 0
                        coroutineScope.launch {
                            pairingManager.pairAndActivate(
                                targetIp = pairingIp.trim(),
                                pairingPort = pPort,
                                pairingCode = pairingCodeText.trim(),
                                connectPort = wPort
                            )
                        }
                    },
                    enabled = !isPairing && pairingIp.isNotBlank() && pairingPortText.isNotBlank() && pairingCodeText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("Pair Device", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    pairingManager.resetState()
                    onDismiss()
                },
                enabled = !isPairing
            ) {
                Text("Close", color = Color(0xFF94A3B8))
            }
        }
    )
}
