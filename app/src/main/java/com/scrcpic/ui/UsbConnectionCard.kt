package com.scrcpic.ui

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrcpic.usb.UsbAdbManager

@Composable
fun UsbConnectionCard(
    discoveredUsbDevice: UsbAdbManager.DiscoveredAdbDevice?,
    isConnecting: Boolean,
    isSwitchingToWireless: Boolean,
    switchStatusMessage: String?,
    switchStatusIsError: Boolean,
    onRefresh: () -> Unit,
    onRequestPermission: (UsbDevice) -> Unit,
    onSwitchToWireless: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0x60111827),
            border = BorderStroke(
                1.dp,
                if (discoveredUsbDevice != null) Color(0xFF06B6D4) else Color(0x33334155)
            ),
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
                                discoveredUsbDevice.device.productName ?: "USB ADB Device"
                            } else {
                                "No USB Device Detected"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White
                        )
                    }

                    Surface(
                        onClick = onRefresh,
                        shape = CircleShape,
                        color = Color(0x2238BDF8),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (discoveredUsbDevice != null) {
                    if (discoveredUsbDevice.hasPermission) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ready to mirror via USB cable (Direct OTG)",
                                fontSize = 11.5.sp,
                                color = Color(0xFF34D399)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Switch to Wireless 1-Touch Button
                        Button(
                            onClick = onSwitchToWireless,
                            enabled = !isSwitchingToWireless && !isConnecting,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0D9488),
                                disabledContainerColor = Color(0x330D9488)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            if (isSwitchingToWireless) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Activating Port 5555...",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "⚡ Switch to Wireless Mode",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { onRequestPermission(discoveredUsbDevice.device) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "Grant USB Permission",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
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

        if (switchStatusMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (switchStatusIsError) Color(0x33EF4444) else Color(0x2610B981)
                ),
                border = BorderStroke(
                    1.dp,
                    if (switchStatusIsError) Color(0x66EF4444) else Color(0x4D10B981)
                )
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
                        text = switchStatusMessage,
                        color = if (switchStatusIsError) Color(0xFFFCA5A5) else Color(0xFFA7F3D0),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
