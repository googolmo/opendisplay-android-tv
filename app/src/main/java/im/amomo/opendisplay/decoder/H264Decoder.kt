package im.amomo.opendisplay.decoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * H264 video decoder using Android's MediaCodec API.
 * Decodes Annex B video frames received over the OpenDisplay protocol and renders them to a Surface.
 */
class H264Decoder {

    private var mediaCodec: MediaCodec? = null
    private var isConfigured = false
    private var surface: Surface? = null
    private var currentWidth = 1920
    private var currentHeight = 1080

    companion object {
        private const val TAG = "H264Decoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
    }

    fun setSurface(surface: Surface?) {
        this.surface = surface
        if (surface == null) {
            stop()
        } else if (isConfigured) {
            // Reconfigure decoder with new surface
            reinitDecoder()
        }
    }

    fun configureResolution(width: Int, height: Int) {
        if (currentWidth != width || currentHeight != height) {
            Log.d(TAG, "Configuring decoder resolution from ${currentWidth}x${currentHeight} to ${width}x${height}")
            currentWidth = width
            currentHeight = height
            if (isConfigured) {
                reinitDecoder()
            }
        }
    }

    private fun reinitDecoder() {
        stop()
        val targetSurface = surface ?: return
        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, currentWidth, currentHeight).apply {
                // Real-time priority mode (0 = realtime priority for minimum latency)
                setInteger(MediaFormat.KEY_PRIORITY, 0)
                // Operating rate hint (e.g. 120 fps) for real-time throughput
                setInteger(MediaFormat.KEY_OPERATING_RATE, 120)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Low latency decoding mode available in Android 11 (API 30+)
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
            }
            val codec = MediaCodec.createDecoderByType(MIME_TYPE)
            codec.configure(format, targetSurface, null, 0)
            codec.start()
            mediaCodec = codec
            isConfigured = true
            Log.d(TAG, "MediaCodec initialized successfully for $currentWidth x $currentHeight (low-latency enabled)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaCodec", e)
            stop()
        }
    }

    fun decodeFrame(videoData: ByteArray, offset: Int, length: Int, timestampUs: Long = System.nanoTime() / 1000) {
        if (surface == null) return
        if (!isConfigured || mediaCodec == null) {
            reinitDecoder()
        }
        val codec = mediaCodec ?: return

        try {
            val inputBufferIndex = codec.dequeueInputBuffer(10000L)
            if (inputBufferIndex >= 0) {
                val inputBuffer: ByteBuffer? = codec.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    inputBuffer.put(videoData, offset, length)
                    codec.queueInputBuffer(inputBufferIndex, 0, length, timestampUs, 0)
                }
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            while (outputBufferIndex >= 0) {
                codec.releaseOutputBuffer(outputBufferIndex, true)
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding frame", e)
            reinitDecoder()
        }
    }

    fun stop() {
        try {
            mediaCodec?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaCodec", e)
        } finally {
            mediaCodec = null
            isConfigured = false
        }
    }
}
