package com.scrcpic.control

import android.util.Log
import dadb.AdbStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.OutputStream

class ScrcpyController(
    private val controlStream: AdbStream,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "ScrcpyController"
    }

    private val outputStream: OutputStream = controlStream.sink.outputStream()
    private val sendChannel = kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    init {
        coroutineScope.launch(Dispatchers.IO) {
            for (data in sendChannel) {
                try {
                    outputStream.write(data)
                    outputStream.flush()
                } catch (e: Exception) {
                    Log.w(TAG, "Error sending control message: ${e.message}")
                    break
                }
            }
        }
    }

    fun sendRaw(data: ByteArray) {
        sendChannel.trySend(data)
    }

    fun sendTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1.0f
    ) {
        val bytes = ControlMessage.createTouchEvent(
            action = action,
            pointerId = pointerId,
            x = x,
            y = y,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            pressure = pressure
        )
        sendRaw(bytes)
    }

    fun pressKey(keycode: Int) {
        val down = ControlMessage.createKeycodeEvent(ControlMessage.ACTION_DOWN, keycode)
        val up = ControlMessage.createKeycodeEvent(ControlMessage.ACTION_UP, keycode)
        sendRaw(down)
        coroutineScope.launch(Dispatchers.IO) {
            delay(50)
            sendRaw(up)
        }
    }

    fun sendBack() {
        val down = ControlMessage.createBackOrScreenOnEvent(ControlMessage.ACTION_DOWN)
        val up = ControlMessage.createBackOrScreenOnEvent(ControlMessage.ACTION_UP)
        sendRaw(down)
        coroutineScope.launch(Dispatchers.IO) {
            delay(50)
            sendRaw(up)
        }
    }

    fun sendHome() = pressKey(ControlMessage.KEYCODE_HOME)
    fun sendRecents() = pressKey(ControlMessage.KEYCODE_APP_SWITCH)
    fun sendPower() = pressKey(ControlMessage.KEYCODE_POWER)
    fun sendVolumeUp() = pressKey(ControlMessage.KEYCODE_VOLUME_UP)
    fun sendVolumeDown() = pressKey(ControlMessage.KEYCODE_VOLUME_DOWN)
    fun sendWakeUp() = pressKey(ControlMessage.KEYCODE_WAKEUP)

    fun setScreenPowerMode(on: Boolean) {
        sendRaw(ControlMessage.createSetDisplayPower(on))
    }

    fun sendScreenOff() = setScreenPowerMode(false)
    fun sendScreenOn() = setScreenPowerMode(true)

    /**
     * Sends RESET_VIDEO control message to force scrcpy-server to immediately
     * generate an IDR keyframe with SPS/PPS to recover/unfreeze video stream.
     */
    fun resetVideo() {
        sendRaw(ControlMessage.createResetVideo())
    }

    fun expandNotificationPanel() {
        sendRaw(ControlMessage.createExpandNotificationPanel())
    }

    fun expandSettingsPanel() {
        sendRaw(ControlMessage.createExpandSettingsPanel())
    }

    fun collapsePanels() {
        sendRaw(ControlMessage.createCollapsePanels())
    }
}
