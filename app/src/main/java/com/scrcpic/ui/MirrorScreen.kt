package com.scrcpic.ui

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.scrcpic.control.ControlMessage
import com.scrcpic.scrcpy.ScrcpySession
import com.scrcpic.scrcpy.SessionState

@SuppressLint("ClickableViewAccessibility")
@Composable
fun MirrorScreen(
    session: ScrcpySession,
    targetIp: String,
    targetPort: Int,
    preset: StreamQualityPreset,
    initialScreenOff: Boolean = true,
    isUsb: Boolean = false,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    val sessionState by session.state.collectAsState()
    val fps by session.fps.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var isLandscape by remember { mutableStateOf(false) }
    var isPhysicalScreenOff by remember { mutableStateOf(initialScreenOff) }

    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.Connected && isPhysicalScreenOff) {
            delay(500)
            session.controller?.setScreenPowerMode(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            session.controller?.setScreenPowerMode(true)
            session.disconnect()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Hardware Video Rendering View with Touch Input
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    isClickable = true
                    isFocusable = true

                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            if (isUsb) {
                                session.connectUsb(
                                    surface = holder.surface,
                                    maxSize = preset.maxSize,
                                    bitRate = preset.bitRate,
                                    maxFps = preset.maxFps
                                )
                            } else {
                                session.connect(
                                    host = targetIp,
                                    port = targetPort,
                                    surface = holder.surface,
                                    maxSize = preset.maxSize,
                                    bitRate = preset.bitRate,
                                    maxFps = preset.maxFps
                                )
                            }
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            session.coordinateTransformer.updateDimensions(
                                videoW = session.scrcpyVideoWidth,
                                videoH = session.scrcpyVideoHeight,
                                containerW = width,
                                containerH = height
                            )
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            session.notifySurfaceDestroyed()
                            session.disconnect(clearError = false)
                        }
                    })

                    setOnTouchListener { v, event ->
                        val ctrl = session.controller
                        val videoW = session.scrcpyVideoWidth
                        val videoH = session.scrcpyVideoHeight

                        if (ctrl != null && videoW > 0 && videoH > 0) {
                            val action = when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> ControlMessage.ACTION_DOWN
                                MotionEvent.ACTION_MOVE -> ControlMessage.ACTION_MOVE
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> ControlMessage.ACTION_UP
                                else -> -1
                            }

                            if (action != -1) {
                                session.coordinateTransformer.updateDimensions(
                                    videoW = videoW,
                                    videoH = videoH,
                                    containerW = v.width,
                                    containerH = v.height
                                )

                                val targetPoint = session.coordinateTransformer.toTargetCoordinates(event.x, event.y)
                                if (targetPoint != null) {
                                    ctrl.sendTouchEvent(
                                        action = action,
                                        pointerId = event.getPointerId(event.actionIndex).toLong(),
                                        x = targetPoint.x,
                                        y = targetPoint.y,
                                        screenWidth = videoW,
                                        screenHeight = videoH,
                                        pressure = event.pressure
                                    )
                                }
                            }
                        }
                        true
                    }
                }
            }
        )

        // 2. Top Status HUD Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xCC0F172A),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Disconnect button on the left (bam side)
                    Surface(
                        onClick = {
                            session.disconnect()
                            onDisconnect()
                        },
                        shape = CircleShape,
                        color = Color(0x33EF4444),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Disconnect",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Subtle vertical separator
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(Color(0xFF334155))
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    val state = sessionState
                    val deviceTitle = if (state is SessionState.Connected) state.deviceName else "Connecting..."
                    val resText = if (state is SessionState.Connected) "${state.width}x${state.height}" else ""

                    Column {
                        Text(
                            text = deviceTitle,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (resText.isNotEmpty()) {
                            Text(
                                text = "$resText • $fps FPS",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(18.dp))

                    // Orientation rotate toggle
                    Surface(
                        onClick = {
                            isLandscape = !isLandscape
                            activity?.requestedOrientation = if (isLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        },
                        shape = CircleShape,
                        color = Color(0x22FFFFFF),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "Rotate",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Eye button (Hide HUD) on the right side
                    Surface(
                        onClick = { showControls = false },
                        shape = CircleShape,
                        color = Color(0x22FFFFFF),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = "Hide HUD",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }

        // Show HUD trigger if hidden
        if (!showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
            ) {
                Surface(
                    onClick = { showControls = true },
                    shape = CircleShape,
                    color = Color(0x99000000),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Show HUD",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 3. Floating Bottom Navigation Dock (Remote Android Buttons)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xCC0F172A),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Remote Back",
                        onClick = { session.controller?.sendBack() }
                    )

                    DockButton(
                        icon = Icons.Default.FiberManualRecord,
                        contentDescription = "Remote Home",
                        onClick = { session.controller?.sendHome() }
                    )

                    DockButton(
                        icon = Icons.Default.CropSquare,
                        contentDescription = "Remote Recents",
                        onClick = { session.controller?.sendRecents() }
                    )

                    DockButton(
                        icon = Icons.Default.PowerSettingsNew,
                        contentDescription = "Remote Power",
                        tint = Color(0xFFF87171),
                        onClick = { session.controller?.sendPower() }
                    )

                    DockButton(
                        icon = if (isPhysicalScreenOff) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isPhysicalScreenOff) "Turn Display On" else "Turn Display Off",
                        tint = if (isPhysicalScreenOff) Color(0xFF38BDF8) else Color(0xFFFBBF24),
                        onClick = {
                            isPhysicalScreenOff = !isPhysicalScreenOff
                            session.controller?.setScreenPowerMode(!isPhysicalScreenOff)
                        }
                    )

                    DockButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh & Reconnect Stream",
                        tint = Color(0xFF34D399),
                        onClick = {
                            session.reconnect()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DockButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0x33FFFFFF),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
