package com.scrcpic.control

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

object ControlMessage {
    const val TYPE_INJECT_KEYCODE = 0
    const val TYPE_INJECT_TEXT = 1
    const val TYPE_INJECT_TOUCH_EVENT = 2
    const val TYPE_INJECT_SCROLL_EVENT = 3
    const val TYPE_BACK_OR_SCREEN_ON = 4
    const val TYPE_EXPAND_NOTIFICATION_PANEL = 5
    const val TYPE_EXPAND_SETTINGS_PANEL = 6
    const val TYPE_COLLAPSE_PANELS = 7
    const val TYPE_GET_CLIPBOARD = 8
    const val TYPE_SET_CLIPBOARD = 9
    const val TYPE_SET_SCREEN_POWER_MODE = 10
    const val TYPE_ROTATE_DEVICE = 11
    const val TYPE_RESET_VIDEO = 17

    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val ACTION_MOVE = 2

    // Android KeyCodes
    const val KEYCODE_HOME = 3
    const val KEYCODE_BACK = 4
    const val KEYCODE_VOLUME_UP = 24
    const val KEYCODE_VOLUME_DOWN = 25
    const val KEYCODE_POWER = 26
    const val KEYCODE_APP_SWITCH = 187 // Recents
    const val KEYCODE_WAKEUP = 224

    /**
     * Creates a touch event packet according to Scrcpy v4.1 protocol.
     * Byte layout:
     * - Type: 1 byte (TYPE_INJECT_TOUCH_EVENT = 2)
     * - Action: 1 byte (0=DOWN, 1=UP, 2=MOVE)
     * - Pointer ID: 8 bytes (Long)
     * - Position X: 4 bytes (Int)
     * - Position Y: 4 bytes (Int)
     * - Screen Width: 2 bytes (UShort)
     * - Screen Height: 2 bytes (UShort)
     * - Pressure: 2 bytes (UShort fixed point, 0xFFFF = 1.0f)
     * - Action Button: 4 bytes (Int)
     * - Buttons: 4 bytes (Int)
     * Total: 32 bytes
     */
    fun createTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1.0f,
        actionButton: Int = when (action) {
            ACTION_DOWN, ACTION_UP -> 1
            else -> 0
        },
        buttons: Int = when (action) {
            ACTION_UP -> 0
            else -> 1
        }
    ): ByteArray {
        val baos = ByteArrayOutputStream(32)
        val dos = DataOutputStream(baos)

        dos.writeByte(TYPE_INJECT_TOUCH_EVENT)
        dos.writeByte(action)
        dos.writeLong(pointerId)
        dos.writeInt(x)
        dos.writeInt(y)
        dos.writeShort(screenWidth and 0xFFFF)
        dos.writeShort(screenHeight and 0xFFFF)
        
        // Convert float pressure (0.0 .. 1.0) to uint16 fixed point
        val u16Pressure = (pressure.coerceIn(0.0f, 1.0f) * 0xFFFF).toInt() and 0xFFFF
        dos.writeShort(u16Pressure)
        dos.writeInt(actionButton)
        dos.writeInt(buttons)
        dos.flush()

        return baos.toByteArray()
    }

    /**
     * Creates a keycode packet according to Scrcpy v4.1 protocol.
     * Byte layout:
     * - Type: 1 byte (TYPE_INJECT_KEYCODE = 0)
     * - Action: 1 byte (0=DOWN, 1=UP)
     * - Keycode: 4 bytes (Int)
     * - Repeat: 4 bytes (Int)
     * - MetaState: 4 bytes (Int)
     * Total: 14 bytes
     */
    fun createKeycodeEvent(
        action: Int,
        keycode: Int,
        repeat: Int = 0,
        metaState: Int = 0
    ): ByteArray {
        val baos = ByteArrayOutputStream(14)
        val dos = DataOutputStream(baos)

        dos.writeByte(TYPE_INJECT_KEYCODE)
        dos.writeByte(action)
        dos.writeInt(keycode)
        dos.writeInt(repeat)
        dos.writeInt(metaState)
        dos.flush()

        return baos.toByteArray()
    }

    /**
     * Creates a back-or-screen-on packet (Type 4)
     * - Action: 1 byte (0=DOWN, 1=UP)
     */
    fun createBackOrScreenOnEvent(action: Int): ByteArray {
        val baos = ByteArrayOutputStream(2)
        val dos = DataOutputStream(baos)
        dos.writeByte(TYPE_BACK_OR_SCREEN_ON)
        dos.writeByte(action)
        dos.flush()
        return baos.toByteArray()
    }

    /**
     * Creates a set display power packet (Type 10)
     * - on: true to turn physical screen ON, false to turn physical screen OFF
     */
    fun createSetDisplayPower(on: Boolean): ByteArray {
        val baos = ByteArrayOutputStream(2)
        val dos = DataOutputStream(baos)
        dos.writeByte(TYPE_SET_SCREEN_POWER_MODE)
        dos.writeBoolean(on)
        dos.flush()
        return baos.toByteArray()
    }

    /**
     * Creates a set screen power mode packet (Type 10)
     * Mode: 0=POWER_MODE_OFF, 2=POWER_MODE_NORMAL
     */
    fun createSetScreenPowerMode(mode: Int): ByteArray {
        return createSetDisplayPower(mode != 0)
    }

    /**
     * Creates a reset video packet (Type 17) to force scrcpy-server
     * to immediately emit an IDR keyframe and re-sync the video stream.
     */
    fun createResetVideo(): ByteArray {
        return byteArrayOf(TYPE_RESET_VIDEO.toByte())
    }
}
