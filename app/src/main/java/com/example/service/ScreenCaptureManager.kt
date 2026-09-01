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
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.model.LogLevel
import com.example.util.Logger
import java.nio.ByteBuffer

class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = 320

    @Volatile
    private var lastCapturedBitmap: Bitmap? = null
    private val frameLock = Any()

    fun initProjection(projection: MediaProjection) {
        this.mediaProjection = projection

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

    private fun setupVirtualDisplay() {
        try {
            imageReader?.close()
            virtualDisplay?.release()

            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            
            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val img = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    processAndCacheImage(img)
                } catch (_: Exception) {}
            }, Handler(Looper.getMainLooper()))

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "CaptchaScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
        } catch (e: Exception) {
            Logger.log("VISION", "VirtualDisplay setup error: ${e.message}", LogLevel.ERROR)
        }
    }

    private fun processAndCacheImage(image: Image) {
        try {
            val planes = image.planes
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
            val finalBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, screenWidth, screenHeight)

            synchronized(frameLock) {
                lastCapturedBitmap = finalBitmap
            }
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }

    /**
     * Captures an instant frame from physical framebuffer or returns the cached live screen
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

    fun release() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (_: Exception) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        synchronized(frameLock) {
            lastCapturedBitmap = null
        }
        Logger.log("VISION", "ScreenCaptureManager resources released.", LogLevel.VISION)
    }
}