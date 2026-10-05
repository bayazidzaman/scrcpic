package com.scrcpic.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import dadb.AdbStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.nio.ByteBuffer

class VideoDecoder(
    private val videoStream: AdbStream,
    private var surface: Surface,
    private val coroutineScope: CoroutineScope,
    private val onConfigured: (deviceName: String, width: Int, height: Int) -> Unit,
    private val onFpsUpdate: (fps: Int) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    companion object {
        private const val TAG = "VideoDecoder"
        private const val FLAG_CONFIG: Long = 1L shl 63      // 0x8000000000000000L
        private const val FLAG_KEY_FRAME: Long = 1L shl 61   // 0x2000000000000000L
        private const val PTS_MASK: Long = 0x1FFFFFFFFFFFFFFFL // bits 0..60
    }

    private val codecLock = Any()
    private var feederJob: Job? = null
    private var renderJob: Job? = null
    private var mediaCodec: MediaCodec? = null

    @Volatile
    private var isRunning = false

    @Volatile
    private var hasValidSurface = true

    @Volatile
    private var cachedFormat: MediaFormat? = null

    @Volatile
    private var cachedMimeType: String? = null

    @Volatile
    private var cachedConfigData: ByteArray? = null

    fun start() {
        if (isRunning) return
        isRunning = true

        feederJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                runDecodePipeline()
            } catch (t: Throwable) {
                if (isRunning && isActive) {
                    val msg = t.message ?: ""
                    if (!msg.contains("Released", ignoreCase = true) && !msg.contains("closed", ignoreCase = true)) {
                        Log.e(TAG, "Video decoding feeder error", t)
                        onError(t)
                    }
                }
            } finally {
                stopInternal()
            }
        }
    }

    private suspend fun CoroutineScope.runDecodePipeline() {
        val dis = DataInputStream(videoStream.source.inputStream())

        // 1. Read dummy byte
        val dummy = dis.readByte()
        Log.d(TAG, "Read dummy byte: 0x%02X".format(dummy))

        // 2. Read 64 bytes device name
        val deviceNameBytes = ByteArray(64)
        dis.readFully(deviceNameBytes)
        val deviceName = String(deviceNameBytes, Charsets.UTF_8).trimEnd { it == '\u0000' }
        Log.d(TAG, "Target device name: $deviceName")

        // 3. Read Codec ID (4 bytes)
        val codecId = dis.readInt()

        // 4. Session metadata (width & height)
        val metaHeader = dis.readInt()
        val rawWidth: Int
        val rawHeight: Int
        if ((metaHeader and 0x80000000.toInt()) != 0) {
            // scrcpy 4+ format: [flags: 4 bytes][width: 4 bytes][height: 4 bytes]
            rawWidth = dis.readInt()
            rawHeight = dis.readInt()
        } else {
            // legacy format: [width: 4 bytes][height: 4 bytes]
            rawWidth = metaHeader
            rawHeight = dis.readInt()
        }

        val width = if (rawWidth > 0) rawWidth else 1280
        val height = if (rawHeight > 0) rawHeight else 720
        Log.d(TAG, "Video stream: codecId=0x%08X, width=$width, height=$height (raw: $rawWidth x $rawHeight)".format(codecId))

        onConfigured(deviceName, width, height)

        // 5. Initialize MediaCodec
        val mimeType = if (codecId == 0x68323635) { // 'h265'
            MediaFormat.MIMETYPE_VIDEO_HEVC
        } else {
            MediaFormat.MIMETYPE_VIDEO_AVC // 'h264'
        }

        val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
            try {
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            } catch (_: Exception) {}
            try {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024) // 1MB buffer
            } catch (_: Exception) {}
        }

        cachedMimeType = mimeType
        cachedFormat = format

        synchronized(codecLock) {
            val codec = MediaCodec.createDecoderByType(mimeType)
            mediaCodec = codec
            codec.configure(format, surface, null, 0)
            codec.start()
        }

        // 6. Launch independent rendering coroutine for low-latency output drain
        renderJob = launch(Dispatchers.IO) {
            val bufferInfo = MediaCodec.BufferInfo()
            var frameCount = 0
            var lastFpsTime = SystemClock.elapsedRealtime()

            while (isRunning && isActive) {
                try {
                    val codec = synchronized(codecLock) { mediaCodec }
                    if (codec == null) {
                        delay(20)
                        continue
                    }

                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outIndex >= 0) {
                        if (hasValidSurface) {
                            try {
                                codec.releaseOutputBuffer(outIndex, true)
                                frameCount++
                            } catch (e: Exception) {
                                try {
                                    codec.releaseOutputBuffer(outIndex, false)
                                } catch (_: Exception) {}
                            }
                        } else {
                            try {
                                codec.releaseOutputBuffer(outIndex, false)
                            } catch (_: Exception) {}
                        }

                        val now = SystemClock.elapsedRealtime()
                        if (now - lastFpsTime >= 1000) {
                            val fps = (frameCount * 1000f / (now - lastFpsTime)).toInt()
                            onFpsUpdate(fps)
                            frameCount = 0
                            lastFpsTime = now
                        }
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.d(TAG, "Decoder output format changed: ${codec.outputFormat}")
                    }
                } catch (e: IllegalStateException) {
                    if (!isRunning || !isActive) break
                    if (e.message?.contains("Released", ignoreCase = true) == true) break
                    Log.w(TAG, "Transient decode state warning: ${e.message}")
                } catch (e: Exception) {
                    if (!isRunning || !isActive) break
                    Log.w(TAG, "Transient decode warning: ${e.message}")
                }
            }
        }

        // 7. Packet reading loop (Feeder)
        var packetData = ByteArray(64 * 1024)
        while (isRunning && isActive) {
            try {
                val ptsAndFlags = dis.readLong()
                val packetSize = dis.readInt()

                if (packetSize <= 0 || packetSize > 5 * 1024 * 1024) {
                    Log.w(TAG, "Invalid packet size: $packetSize, terminating video loop")
                    break
                }

                if (packetData.size < packetSize) {
                    packetData = ByteArray(packetSize)
                }
                dis.readFully(packetData, 0, packetSize)

                val isConfig = (ptsAndFlags and FLAG_CONFIG) != 0L
                val isKeyFrame = (ptsAndFlags and FLAG_KEY_FRAME) != 0L
                val pts = if (isConfig) 0L else (ptsAndFlags and PTS_MASK)

                // Cache the SPS/PPS config packet so we can re-create MediaCodec on the fly anytime
                if (isConfig) {
                    val configBytes = ByteArray(packetSize)
                    System.arraycopy(packetData, 0, configBytes, 0, packetSize)
                    cachedConfigData = configBytes
                    Log.d(TAG, "Cached SPS/PPS video config header (${configBytes.size} bytes)")
                }

                var flags = 0
                if (isConfig) flags = flags or MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                if (isKeyFrame) flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME

                var inputIndex = -1
                var targetCodec: MediaCodec? = null
                var retries = 0

                while (inputIndex < 0 && isRunning && isActive && retries < 20) {
                    synchronized(codecLock) {
                        targetCodec = mediaCodec
                        if (targetCodec != null && isRunning && isActive) {
                            try {
                                inputIndex = targetCodec!!.dequeueInputBuffer(10_000)
                            } catch (_: Exception) {
                                inputIndex = -1
                            }
                        }
                    }
                    if (inputIndex < 0 && isRunning && isActive) {
                        retries++
                        delay(5)
                    }
                }

                if (inputIndex >= 0 && isRunning && isActive) {
                    synchronized(codecLock) {
                        val codec = mediaCodec
                        if (codec != null && codec == targetCodec && isRunning && isActive) {
                            try {
                                val inputBuffer: ByteBuffer? = codec.getInputBuffer(inputIndex)
                                if (inputBuffer != null) {
                                    inputBuffer.clear()
                                    inputBuffer.put(packetData, 0, packetSize)
                                    codec.queueInputBuffer(inputIndex, 0, packetSize, pts, flags)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error queuing input buffer: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: IllegalStateException) {
                if (!isRunning || !isActive) break
                if (e.message?.contains("Released", ignoreCase = true) == true) break
                throw e
            } catch (e: java.io.IOException) {
                if (!isRunning || !isActive) break
                throw e
            }
        }
    }

    fun notifySurfaceDestroyed() {
        hasValidSurface = false
        Log.d(TAG, "Surface destroyed - background frame draining active")
    }

    fun setOutputSurface(newSurface: Surface) {
        surface = newSurface
        synchronized(codecLock) {
            // Recreating the codec on the new surface is the robust, OEM-agnostic solution.
            // On Redmi/MIUI and many Qualcomm/MediaTek devices, MediaCodec.setOutputSurface
            // fails silently (returns without error but leaves the display black).
            // Recreating with cached SPS/PPS + requesting an immediate IDR keyframe restores
            // video rendering immediately.
            recreateCodecOnSurface(newSurface)
        }
    }

    private fun recreateCodecOnSurface(newSurface: Surface) {
        val format = cachedFormat ?: return
        val mimeType = cachedMimeType ?: return
        val configData = cachedConfigData

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (_: Exception) {}
        mediaCodec = null

        try {
            if (configData != null) {
                try {
                    format.setByteBuffer("csd-0", ByteBuffer.wrap(configData))
                } catch (_: Exception) {}
            }

            val newCodec = MediaCodec.createDecoderByType(mimeType)
            newCodec.configure(format, newSurface, null, 0)
            newCodec.start()

            // Feed cached SPS/PPS config so decoder is immediately primed
            if (configData != null) {
                try {
                    val inputIndex = newCodec.dequeueInputBuffer(50_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = newCodec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            inputBuffer.put(configData)
                            newCodec.queueInputBuffer(inputIndex, 0, configData.size, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                            Log.d(TAG, "Fed cached SPS/PPS config to newly recreated MediaCodec")
                        }
                    }
                } catch (_: Exception) {}
            }

            mediaCodec = newCodec
            hasValidSurface = true
            Log.d(TAG, "MediaCodec successfully recreated and bound to new surface!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recreate MediaCodec on new surface", e)
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        renderJob?.cancel()
        renderJob = null
        feederJob?.cancel()
        feederJob = null
        stopInternal()
    }

    private fun stopInternal() {
        synchronized(codecLock) {
            try {
                mediaCodec?.stop()
            } catch (_: Exception) {}
            try {
                mediaCodec?.release()
            } catch (_: Exception) {}
            mediaCodec = null
        }
    }
}
