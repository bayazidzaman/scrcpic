package com.scrcpic

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.scrcpic.scrcpy.ScrcpySession
import com.scrcpic.ui.ConnectionScreen
import com.scrcpic.ui.MirrorScreen
import com.scrcpic.ui.StreamQualityPreset
import com.scrcpic.ui.theme.ScrcpicTheme

data class ConnectionParams(
    val ip: String,
    val port: Int,
    val preset: StreamQualityPreset,
    val turnScreenOff: Boolean = true,
    val isUsb: Boolean = false
)

class MainActivity : ComponentActivity() {

    private lateinit var scrcpySession: ScrcpySession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on while app is open
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        scrcpySession = ScrcpySession(this, lifecycleScope)

        setContent {
            ScrcpicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF080C14)
                ) {
                    var connectionParams by remember { mutableStateOf<ConnectionParams?>(null) }
                    val sessionState by scrcpySession.state.collectAsState()

                    val isConnected = connectionParams != null

                    // Request notification permission on Android 13+ for foreground service status
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                        ) {}
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }

                    androidx.compose.runtime.DisposableEffect(isConnected) {
                        setImmersiveMode(isConnected)
                        if (isConnected) {
                            try {
                                val targetAddr = if (connectionParams!!.isUsb) "USB OTG Device" else "${connectionParams!!.ip}:${connectionParams!!.port}"
                                com.scrcpic.service.MirroringService.start(
                                    this@MainActivity,
                                    targetAddr
                                )
                            } catch (t: Throwable) {
                                android.util.Log.w("MainActivity", "MirroringService start fallback: ${t.message}")
                            }
                        }
                        onDispose {
                            if (isConnected) {
                                setImmersiveMode(false)
                                try {
                                    com.scrcpic.service.MirroringService.stop(this@MainActivity)
                                } catch (_: Throwable) {}
                            }
                        }
                    }

                    var lastHomeBackTime by remember { mutableStateOf(0L) }

                    if (connectionParams == null) {
                        BackHandler {
                            val now = System.currentTimeMillis()
                            if (now - lastHomeBackTime < 2000L) {
                                finish()
                            } else {
                                lastHomeBackTime = now
                                Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                            }
                        }

                        ConnectionScreen(
                            sessionState = sessionState,
                            usbAdbManager = scrcpySession.usbAdbManager,
                            onConnect = { ip, port, preset, turnScreenOff, isUsb ->
                                connectionParams = ConnectionParams(ip, port, preset, turnScreenOff, isUsb)
                            }
                        )
                    } else {
                        // Forward system back gesture / edge swipe directly to the remote phone
                        BackHandler {
                            scrcpySession.controller?.sendBack()
                        }

                        MirrorScreen(
                            session = scrcpySession,
                            targetIp = connectionParams!!.ip,
                            targetPort = connectionParams!!.port,
                            preset = connectionParams!!.preset,
                            initialScreenOff = connectionParams!!.turnScreenOff,
                            isUsb = connectionParams!!.isUsb,
                            onDisconnect = {
                                scrcpySession.disconnect()
                                connectionParams = null
                            }
                        )
                    }
                }
            }
        }
    }

    private fun setImmersiveMode(immersive: Boolean) {
        try {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (immersive) {
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            com.scrcpic.service.MirroringService.stop(this)
        } catch (_: Throwable) {}
        scrcpySession.disconnect()
    }
}