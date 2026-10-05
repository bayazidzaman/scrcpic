package com.scrcpic.video

data class TargetPoint(val x: Int, val y: Int)

class CoordinateTransformer {

    private var videoWidth = 1080
    private var videoHeight = 2400
    private var containerWidth = 1080
    private var containerHeight = 2400

    private var renderLeft = 0f
    private var renderTop = 0f
    private var renderRight = 1080f
    private var renderBottom = 2400f

    fun updateDimensions(videoW: Int, videoH: Int, containerW: Int, containerH: Int) {
        if (videoW <= 0 || videoH <= 0 || containerW <= 0 || containerH <= 0) return
        this.videoWidth = videoW
        this.videoHeight = videoH
        this.containerWidth = containerW
        this.containerHeight = containerH

        val containerAspect = containerW.toFloat() / containerH.toFloat()
        val videoAspect = videoW.toFloat() / videoH.toFloat()

        if (containerAspect > videoAspect) {
            // Container is wider than video: Pillarbox (black bars on left & right)
            val renderW = containerH.toFloat() * videoAspect
            val left = (containerW - renderW) / 2f
            renderLeft = left
            renderTop = 0f
            renderRight = left + renderW
            renderBottom = containerH.toFloat()
        } else {
            // Container is taller than video: Letterbox (black bars on top & bottom)
            val renderH = containerW.toFloat() / videoAspect
            val top = (containerH - renderH) / 2f
            renderLeft = 0f
            renderTop = top
            renderRight = containerW.toFloat()
            renderBottom = top + renderH
        }
    }

    fun toTargetCoordinates(touchX: Float, touchY: Float): TargetPoint? {
        val renderW = renderRight - renderLeft
        val renderH = renderBottom - renderTop
        if (renderW <= 0f || renderH <= 0f) return null

        val clampedX = touchX.coerceIn(renderLeft, renderRight)
        val clampedY = touchY.coerceIn(renderTop, renderBottom)

        val normalizedX = (clampedX - renderLeft) / renderW
        val normalizedY = (clampedY - renderTop) / renderH

        val targetX = (normalizedX * videoWidth).toInt().coerceIn(0, videoWidth)
        val targetY = (normalizedY * videoHeight).toInt().coerceIn(0, videoHeight)

        return TargetPoint(targetX, targetY)
    }

    val currentVideoWidth: Int get() = videoWidth
    val currentVideoHeight: Int get() = videoHeight
    val currentContainerWidth: Int get() = containerWidth
    val currentContainerHeight: Int get() = containerHeight
}
