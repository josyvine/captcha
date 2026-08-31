package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
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

    fun initProjection(projection: MediaProjection) {
        this.mediaProjection = projection
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)

        screenWidth = if (metrics.widthPixels > 0) metrics.widthPixels else 720
        screenHeight = if (metrics.heightPixels > 0) metrics.heightPixels else 1280
        screenDensity = if (metrics.densityDpi > 0) metrics.densityDpi else 320

        setupVirtualDisplay()
        Logger.log("VISION", "ScreenCaptureManager initialized with resolution: ${screenWidth}x${screenHeight} (${screenDensity}dpi).", LogLevel.VISION)
    }

    private fun setupVirtualDisplay() {
        try {
            imageReader?.close()
            virtualDisplay?.release()

            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
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

    /**
     * Captures an instant frame from physical framebuffer
     */
    fun captureFrame(): Bitmap? {
        var bitmap: Bitmap? = null
        var image: Image? = null
        try {
            image = imageReader?.acquireLatestImage()
            if (image != null) {
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
                bitmap = Bitmap.createBitmap(rawBitmap, 0, 0, screenWidth, screenHeight)
                Logger.log("VISION", "Acquired physical hardware frame (${screenWidth}x${screenHeight}) from GPU framebuffer.", LogLevel.VISION)
            }
        } catch (e: Exception) {
            Logger.log("VISION", "Framebuffer acquisition notice: ${e.message}", LogLevel.INFO)
        } finally {
            image?.close()
        }

        return bitmap
    }

    /**
     * Generates an authentic sample 2Captcha puzzle canvas for interactive testing
     */
    fun generateRealisticTestCaptcha(instructionType: Int = (0..3).random()): Bitmap {
        val width = 480
        val height = 240
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Grid noise lines
        val linePaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            strokeWidth = 2f
        }
        for (i in 0..width step 40) {
            canvas.drawLine(i.toFloat(), 0f, i.toFloat(), height.toFloat(), linePaint)
        }
        for (j in 0..height step 40) {
            canvas.drawLine(0f, j.toFloat(), width.toFloat(), j.toFloat(), linePaint)
        }

        // Header directive badge
        val headerPaint = Paint().apply {
            color = Color.parseColor("#00F0FF")
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.parseColor("#FFFFFF")
            textSize = 34f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 14f
            isAntiAlias = true
        }

        when (instructionType) {
            0 -> {
                canvas.drawText("2CAPTCHA: Enter numeral in digits", 20f, 40f, headerPaint)
                canvas.drawText("nine hundred and eighty four", 30f, 130f, textPaint)
                canvas.drawText("Target: 984 | min 1 max 5", 30f, 190f, subPaint)
            }
            1 -> {
                canvas.drawText("2CAPTCHA: Solve Arithmetic Equation", 20f, 40f, headerPaint)
                canvas.drawText("26 + 6 = ?", 140f, 130f, textPaint)
                canvas.drawText("Calculate exact numeric result", 30f, 190f, subPaint)
            }
            2 -> {
                canvas.drawText("2CAPTCHA: Multi-Frame Character Sequence", 20f, 40f, headerPaint)
                canvas.drawText("[ M U A G 6 T ]", 80f, 130f, textPaint)
                canvas.drawText("min 6 | max 6 characters", 30f, 190f, subPaint)
            }
            else -> {
                canvas.drawText("2CAPTCHA: Missing Sequence Number", 20f, 40f, headerPaint)
                canvas.drawText("_ , 5 , 4 , 3", 110f, 130f, textPaint)
                canvas.drawText("Fill in missing start or end value", 30f, 190f, subPaint)
            }
        }

        return bmp
    }

    fun release() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        Logger.log("VISION", "ScreenCaptureManager resources released.", LogLevel.VISION)
    }
}
