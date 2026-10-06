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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrcpic.R
import com.scrcpic.scrcpy.SessionState

@Composable
fun ConnectionHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "Scrcpic Logo",
            modifier = Modifier.size(76.dp)
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
    }
}

@Composable
fun ConnectionModeSelector(
    selectedMode: ConnectionMode,
    isConnecting: Boolean,
    onSelectMode: (ConnectionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x601E293B), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            onClick = { if (!isConnecting) onSelectMode(ConnectionMode.WIFI) },
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
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Wi-Fi Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }

        Surface(
            onClick = { if (!isConnecting) onSelectMode(ConnectionMode.USB) },
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
                Icon(
                    Icons.Default.Usb,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("USB Cable", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

@Composable
fun PairingStatusChip(
    isPaired: Boolean,
    targetIp: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isPaired) Color(0x1810B981) else Color(0x2038BDF8),
        border = BorderStroke(1.dp, if (isPaired) Color(0x4D10B981) else Color(0x4038BDF8)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPaired) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text("⚡", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isPaired) "Pairing Validated" else "Wireless Pairing Assistant",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPaired) Color(0xFF34D399) else Color(0xFF38BDF8)
                        )
                        if (isPaired) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0x3310B981),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6EE7B7),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isPaired) {
                            "Paired with $targetIp • Tap to manage or re-pair"
                        } else {
                            "Pair wirelessly via 6-digit code (Android 11+)"
                        },
                        fontSize = 10.5.sp,
                        color = if (isPaired) Color(0xFFA7F3D0) else Color(0xFF94A3B8)
                    )
                }
            }
            Text(
                text = if (isPaired) "Manage ❯" else "Pair ❯",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPaired) Color(0xFF34D399) else Color(0xFF38BDF8)
            )
        }
    }
}

@Composable
fun PresetTile(
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

@Composable
fun ScreenOffToggle(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { if (enabled) onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = Color(0x60111827),
        border = BorderStroke(1.dp, Color(0x22334155)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (checked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = null,
                tint = if (checked) Color(0xFF38BDF8) else Color(0xFF64748B),
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
                checked = checked,
                onCheckedChange = { if (enabled) onCheckedChange(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4F46E5),
                    uncheckedThumbColor = Color(0xFF94A3B8),
                    uncheckedTrackColor = Color(0xFF1E293B)
                )
            )
        }
    }
}

@Composable
fun ConnectActionButton(
    isConnecting: Boolean,
    isUsbMode: Boolean,
    canConnect: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isConnecting && canConnect,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(
                10.dp,
                RoundedCornerShape(14.dp),
                spotColor = if (isUsbMode) Color(0xFF06B6D4) else Color(0xFF4F46E5)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color(0x40334155)
        ),
        contentPadding = PaddingValues(0.dp)
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
}

@Composable
fun ConnectionStatusBanner(
    sessionState: SessionState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = sessionState is SessionState.Connecting || sessionState is SessionState.Error,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
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
}
