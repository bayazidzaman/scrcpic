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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.scrcpic.control.ControlMessage
import com.scrcpic.scrcpy.ScrcpySession
import com.scrcpic.scrcpy.SessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    var isPhysicalScreenOff by remember { mutableStateOf(initialScreenOff) }

    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.Connected) {
            delay(500)
            if (isPhysicalScreenOff) {
                session.controller?.setScreenPowerMode(false)
            }
            session.pushLocalClipboardToRemote()
        }
    }

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            session.controller?.setScreenPowerMode(true)
            session.disconnect()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    delay(500)
                    session.pushLocalClipboardToRemote()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val density = LocalDensity.current
        val fabSizePx = with(density) { 46.dp.toPx() }
        val maxFabX = (constraints.maxWidth - fabSizePx).coerceAtLeast(0f)
        val maxFabY = (constraints.maxHeight - fabSizePx).coerceAtLeast(0f)

        var fabOffsetX by remember { mutableStateOf(-1f) }
        var fabOffsetY by remember { mutableStateOf(-1f) }

        LaunchedEffect(constraints.maxWidth, constraints.maxHeight) {
            if (fabOffsetX < 0f && constraints.maxWidth > 0) {
                fabOffsetX = maxFabX - with(density) { 16.dp.toPx() }
                fabOffsetY = maxFabY / 2f // Middle of the screen
            }
        }

        val state = sessionState
        val videoRatio = if (state is SessionState.Connected && state.height > 0) {
            state.width.toFloat() / state.height.toFloat()
        } else {
            null
        }

        // 1. Hardware Video Rendering View with Edge Gesture & Touch Detection
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = if (videoRatio != null) {
                    Modifier.aspectRatio(videoRatio)
                } else {
                    Modifier.fillMaxSize()
                },
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                    isClickable = true
                    isFocusable = true

                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            if (session.isSessionAlive()) {
                                session.onSurfaceRestored(holder.surface)
                            } else {
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
                            // Keep ADB stream & scrcpy-server alive in background across lock / minimize
                            session.notifySurfaceDestroyed()
                        }
                    })

                    var lastMoveTimestamp = 0L
                    val moveThrottleMs = 8L // ~120 FPS move ceiling to avoid TCP socket congestion while maintaining instant responsiveness

                    setOnTouchListener { v, event ->
                        val ctrl = session.controller
                        val videoW = session.scrcpyVideoWidth
                        val videoH = session.scrcpyVideoHeight

                        if (ctrl != null && videoW > 0 && videoH > 0) {
                            session.coordinateTransformer.updateDimensions(
                                videoW = videoW,
                                videoH = videoH,
                                containerW = v.width,
                                containerH = v.height
                            )

                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                                    val index = event.actionIndex
                                    val pointerId = event.getPointerId(index).toLong()
                                    val targetPoint = session.coordinateTransformer.toTargetCoordinates(event.getX(index), event.getY(index))
                                    if (targetPoint != null) {
                                        ctrl.sendTouchEvent(
                                            action = ControlMessage.ACTION_DOWN,
                                            pointerId = pointerId,
                                            x = targetPoint.x,
                                            y = targetPoint.y,
                                            screenWidth = videoW,
                                            screenHeight = videoH,
                                            pressure = event.getPressure(index)
                                        )
                                    }
                                }

                                MotionEvent.ACTION_MOVE -> {
                                    val now = android.os.SystemClock.uptimeMillis()
                                    if (now - lastMoveTimestamp >= moveThrottleMs) {
                                        lastMoveTimestamp = now
                                        for (index in 0 until event.pointerCount) {
                                            val pointerId = event.getPointerId(index).toLong()
                                            val targetPoint = session.coordinateTransformer.toTargetCoordinates(event.getX(index), event.getY(index))
                                            if (targetPoint != null) {
                                                ctrl.sendTouchEvent(
                                                    action = ControlMessage.ACTION_MOVE,
                                                    pointerId = pointerId,
                                                    x = targetPoint.x,
                                                    y = targetPoint.y,
                                                    screenWidth = videoW,
                                                    screenHeight = videoH,
                                                    pressure = event.getPressure(index)
                                                )
                                            }
                                        }
                                    }
                                }

                                MotionEvent.ACTION_POINTER_UP -> {
                                    val index = event.actionIndex
                                    val pointerId = event.getPointerId(index).toLong()
                                    val targetPoint = session.coordinateTransformer.toTargetCoordinates(event.getX(index), event.getY(index))
                                    if (targetPoint != null) {
                                        ctrl.sendTouchEvent(
                                            action = ControlMessage.ACTION_UP,
                                            pointerId = pointerId,
                                            x = targetPoint.x,
                                            y = targetPoint.y,
                                            screenWidth = videoW,
                                            screenHeight = videoH,
                                            pressure = 0f
                                        )
                                    }
                                }

                                MotionEvent.ACTION_UP -> {
                                    val index = event.actionIndex
                                    val pointerId = event.getPointerId(index).toLong()
                                    val targetPoint = session.coordinateTransformer.toTargetCoordinates(event.getX(index), event.getY(index))
                                    if (targetPoint != null) {
                                        ctrl.sendTouchEvent(
                                            action = ControlMessage.ACTION_UP,
                                            pointerId = pointerId,
                                            x = targetPoint.x,
                                            y = targetPoint.y,
                                            screenWidth = videoW,
                                            screenHeight = videoH,
                                            pressure = 0f
                                        )
                                    }
                                }

                                MotionEvent.ACTION_CANCEL -> {
                                    for (index in 0 until event.pointerCount) {
                                        val pointerId = event.getPointerId(index).toLong()
                                        val targetPoint = session.coordinateTransformer.toTargetCoordinates(event.getX(index), event.getY(index))
                                        if (targetPoint != null) {
                                            ctrl.sendTouchEvent(
                                                action = ControlMessage.ACTION_UP,
                                                pointerId = pointerId,
                                                x = targetPoint.x,
                                                y = targetPoint.y,
                                                screenWidth = videoW,
                                                screenHeight = videoH,
                                                pressure = 0f
                                            )
                                        }
                                    }
                                }
                            }
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        )
        }

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
                    // Disconnect button on the left
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

                    // Vertical separator
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
                }
            }
        }

        // 3. Floating Bottom Navigation Dock (Essential 5 Buttons: Back, Home, Recents, Power, Display Off/On)
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
                        contentDescription = "Remote Home (Minimize)",
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
                        icon = if (isPhysicalScreenOff) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = if (isPhysicalScreenOff) "Turn Display On" else "Turn Display Off",
                        tint = if (isPhysicalScreenOff) Color(0xFF38BDF8) else Color(0xFFFBBF24),
                        onClick = {
                            isPhysicalScreenOff = !isPhysicalScreenOff
                            session.controller?.setScreenPowerMode(!isPhysicalScreenOff)
                        }
                    )
                }
            }
        }

        // 4. Draggable Floating Eye Button (Can be dragged freely anywhere across the screen)
        Surface(
            onClick = { showControls = !showControls },
            shape = CircleShape,
            color = Color(0xCC0F172A),
            border = BorderStroke(1.dp, Color(0x3338BDF8)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .offset {
                    val x = if (fabOffsetX >= 0f) fabOffsetX.roundToInt() else 0
                    val y = if (fabOffsetY >= 0f) fabOffsetY.roundToInt() else 0
                    IntOffset(x, y)
                }
                .size(46.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        fabOffsetX = (fabOffsetX + dragAmount.x).coerceIn(0f, maxFabX)
                        fabOffsetY = (fabOffsetY + dragAmount.y).coerceIn(0f, maxFabY)
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (showControls) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showControls) "Hide Controls" else "Show Controls",
                    tint = if (showControls) Color(0xFF94A3B8) else Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
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
