package com.scrcpic

import com.scrcpic.control.ControlMessage
import com.scrcpic.video.CoordinateTransformer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.ByteBuffer

class ScrcpyProtocolTest {


    @Test
    fun testTouchEventSerialization() {
        val bytes = ControlMessage.createTouchEvent(
            action = ControlMessage.ACTION_DOWN,
            pointerId = 1L,
            x = 540,
            y = 960,
            screenWidth = 1080,
            screenHeight = 2400,
            pressure = 1.0f,
            actionButton = 1,
            buttons = 1
        )

        assertEquals(32, bytes.size)
        val buf = ByteBuffer.wrap(bytes)

        // 1 byte: type
        assertEquals(ControlMessage.TYPE_INJECT_TOUCH_EVENT.toByte(), buf.get())
        // 1 byte: action
        assertEquals(ControlMessage.ACTION_DOWN.toByte(), buf.get())
        // 8 bytes: pointerId
        assertEquals(1L, buf.long)
        // 4 bytes: x
        assertEquals(540, buf.int)
        // 4 bytes: y
        assertEquals(960, buf.int)
        // 2 bytes: screenWidth
        assertEquals(1080, buf.short.toInt() and 0xFFFF)
        // 2 bytes: screenHeight
        assertEquals(2400, buf.short.toInt() and 0xFFFF)
        // 2 bytes: pressure (1.0f * 0xFFFF)
        assertEquals(0xFFFF, buf.short.toInt() and 0xFFFF)
        // 4 bytes: actionButton
        assertEquals(1, buf.int)
        // 4 bytes: buttons
        assertEquals(1, buf.int)
    }

    @Test
    fun testKeycodeEventSerialization() {
        val bytes = ControlMessage.createKeycodeEvent(
            action = ControlMessage.ACTION_DOWN,
            keycode = ControlMessage.KEYCODE_HOME,
            repeat = 0,
            metaState = 0
        )

        assertEquals(14, bytes.size)
        val buf = ByteBuffer.wrap(bytes)

        // 1 byte: type
        assertEquals(ControlMessage.TYPE_INJECT_KEYCODE.toByte(), buf.get())
        // 1 byte: action
        assertEquals(ControlMessage.ACTION_DOWN.toByte(), buf.get())
        // 4 bytes: keycode
        assertEquals(ControlMessage.KEYCODE_HOME, buf.int)
        // 4 bytes: repeat
        assertEquals(0, buf.int)
        // 4 bytes: metaState
        assertEquals(0, buf.int)
    }

    @Test
    fun testCoordinateTransformer() {
        val transformer = CoordinateTransformer()
        transformer.updateDimensions(
            videoW = 1000,
            videoH = 2000,
            containerW = 1000,
            containerH = 2000
        )

        val centerPoint = transformer.toTargetCoordinates(500f, 1000f)
        println("centerPoint: $centerPoint")
        assertNotNull(centerPoint)
        assertEquals(500, centerPoint!!.x)
        assertEquals(1000, centerPoint.y)

        val cornerPoint = transformer.toTargetCoordinates(0f, 0f)
        println("cornerPoint: $cornerPoint")
        assertNotNull(cornerPoint)
        assertEquals(0, cornerPoint!!.x)
        assertEquals(0, cornerPoint.y)
    }

    @Test
    fun testResetVideoSerialization() {
        val bytes = ControlMessage.createResetVideo()
        assertEquals(1, bytes.size)
        assertEquals(ControlMessage.TYPE_RESET_VIDEO.toByte(), bytes[0])
    }
}
