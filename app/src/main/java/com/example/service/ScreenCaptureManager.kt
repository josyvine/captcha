package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.model.LogLevel
import com.example.util.Logger
import java.nio.ByteBuffer

class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = 320

    @Volatile
    private var lastCapturedBitmap: Bitmap? = null

    @Volatile
    private var lastCaptureTimestamp: Long = 0L

    private val frameLock = Any()

    fun initProjection(projection: MediaProjection) {
        this.mediaProjection = projection

        // Start dedicated high-priority background thread for frame processing
        startBackgroundThread()

        // Register required callback for Android 14+ safety
        try {
            this.mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Logger.log("VISION", "ScreenCaptureManager: MediaProjection stopped.", LogLevel.INFO)
                    release()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Logger.log("VISION", "Callback registration note: ${e.message}", LogLevel.INFO)
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenWidth = bounds.width().coerceAtLeast(720)
            screenHeight = bounds.height().coerceAtLeast(1280)
            screenDensity = context.resources.displayMetrics.densityDpi.coerceAtLeast(320)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(metrics)
            screenWidth = if (metrics.widthPixels > 0) metrics.widthPixels else 720
            screenHeight = if (metrics.heightPixels > 0) metrics.heightPixels else 1280
            screenDensity = if (metrics.densityDpi > 0) metrics.densityDpi else 320
        }

        // Align dimensions to even numbers for ImageReader stride safety
        screenWidth = (screenWidth / 2) * 2
        screenHeight = (screenHeight / 2) * 2

        setupVirtualDisplay()
        Logger.log("VISION", "ScreenCaptureManager initialized with physical resolution: ${screenWidth}x${screenHeight} (${screenDensity}dpi).", LogLevel.VISION)
    }

    private fun startBackgroundThread() {
        stopBackgroundThread()
        backgroundThread = HandlerThread("ScreenCaptureBackground", Process.THREAD_PRIORITY_URGENT_DISPLAY).apply {
            start()
            backgroundHandler = Handler(looper)
        }
    }

    private fun stopBackgroundThread() {
        try {
            backgroundThread?.quitSafely()
            backgroundThread?.join(500)
        } catch (_: Exception) {}
        backgroundThread = null
        backgroundHandler = null
    }

    private fun setupVirtualDisplay() {
        try {
            imageReader?.close()
            virtualDisplay?.release()

            // 2 buffers: Acquire newest, drop stale
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            
            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val img = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    processAndCacheImage(img)
                } catch (_: Exception) {}
            }, backgroundHandler ?: Handler(Looper.getMainLooper()))

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "CaptchaScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                backgroundHandler
            )
        } catch (e: Exception) {
            Logger.log("VISION", "VirtualDisplay setup error: ${e.message}", LogLevel.ERROR)
        }
    }

    private fun processAndCacheImage(image: Image) {
        try {
            val planes = image.planes
            if (planes.isEmpty()) return

            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val rawBitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            rawBitmap.copyPixelsFromBuffer(buffer)
            
            val finalBitmap = if (rowPadding == 0) {
                rawBitmap
            } else {
                val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, screenWidth, screenHeight)
                rawBitmap.recycle()
                cropped
            }

            synchronized(frameLock) {
                lastCapturedBitmap = finalBitmap
                lastCaptureTimestamp = System.currentTimeMillis()
            }
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }

    /**
     * Purges stale frame cache to ensure the subsequent capture reads brand-new screen content
     */
    fun clearFrameBuffer() {
        synchronized(frameLock) {
            lastCapturedBitmap = null
            lastCaptureTimestamp = 0L
        }
        try {
            // Drain ImageReader pipeline
            var staleImg = imageReader?.acquireLatestImage()
            while (staleImg != null) {
                staleImg.close()
                staleImg = imageReader?.acquireLatestImage()
            }
        } catch (_: Exception) {}
    }

    /**
     * Captures an instant frame from the physical framebuffer, draining stale buffers first
     */
    fun captureFrame(): Bitmap? {
        var image: Image? = null
        try {
            image = imageReader?.acquireLatestImage()
            if (image != null) {
                processAndCacheImage(image)
            }
        } catch (e: Exception) {
            Logger.log("VISION", "Framebuffer acquisition notice: ${e.message}", LogLevel.INFO)
        } finally {
            image?.close()
        }

        synchronized(frameLock) {
            return lastCapturedBitmap?.let { Bitmap.createBitmap(it) }
        }
    }

    fun getLastFrameTimestamp(): Long = lastCaptureTimestamp

    fun release() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (_: Exception) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        stopBackgroundThread()
        synchronized(frameLock) {
            lastCapturedBitmap = null
            lastCaptureTimestamp = 0L
        }
        Logger.log("VISION", "ScreenCaptureManager resources released.", LogLevel.VISION)
    }
}