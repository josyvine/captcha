package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.model.LogLevel
import com.example.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = 320

    private var captureThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    @Volatile
    private var latestFrameBitmap: Bitmap? = null
    private val frameLock = Any()

    companion object {
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "screen_capture_service_channel"

        private const val ACTION_START = "com.example.service.ACTION_START_CAPTURE"
        private const val ACTION_STOP = "com.example.service.ACTION_STOP_CAPTURE"
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_DATA = "extra_data"

        private val _isCapturing = MutableStateFlow(false)
        val isCapturing = _isCapturing.asStateFlow()

        var instance: ScreenCaptureService? = null
            private set

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        captureThread = HandlerThread("ScreenCaptureBackgroundThread").apply { start() }
        backgroundHandler = Handler(captureThread!!.looper)

        try {
            startForegroundWithNotification()
        } catch (e: Throwable) {
            Logger.log("VISION", "ScreenCaptureService startForeground in onCreate: ${e.message}", LogLevel.INFO)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }

                if (resultCode != 0 && resultData != null) {
                    try {
                        startForegroundWithNotification()
                        initMediaProjection(resultCode, resultData)
                    } catch (e: Throwable) {
                        Logger.log("VISION", "initMediaProjection failed: ${e.message}", LogLevel.ERROR)
                    }
                } else {
                    Logger.log("VISION", "ScreenCaptureService received empty permission result.", LogLevel.ERROR)
                }
            }
            ACTION_STOP -> {
                cleanup()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Logger.log("VISION", "Notification startForeground fallback: ${e.message}", LogLevel.INFO)
            try {
                startForeground(NOTIFICATION_ID, buildNotification())
            } catch (_: Throwable) {}
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("2Captcha Screen Capture Active")
            .setContentText("Reading live screen framebuffer in real-time")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Screen Capture Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Captures live screen frames for 2Captcha visual reasoning"
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }
        } catch (_: Throwable) {}
    }

    private fun initMediaProjection(resultCode: Int, data: Intent) {
        try {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                Logger.log("VISION", "Failed to retrieve MediaProjection from system.", LogLevel.ERROR)
                _isCapturing.value = false
                return
            }

            // Android 14 (API 34+) STRICT REQUIREMENT: Register Callback BEFORE createVirtualDisplay
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Logger.log("VISION", "MediaProjection stopped by Android OS.", LogLevel.INFO)
                    cleanup()
                    _isCapturing.value = false
                }
            }, backgroundHandler ?: Handler(Looper.getMainLooper()))

            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                screenWidth = bounds.width().coerceAtLeast(720)
                screenHeight = bounds.height().coerceAtLeast(1280)
                screenDensity = resources.displayMetrics.densityDpi.coerceAtLeast(320)
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(metrics)
                screenWidth = if (metrics.widthPixels > 0) metrics.widthPixels else 720
                screenHeight = if (metrics.heightPixels > 0) metrics.heightPixels else 1280
                screenDensity = if (metrics.densityDpi > 0) metrics.densityDpi else 320
            }

            // Round to even numbers for ImageReader buffer safety
            screenWidth = (screenWidth / 2) * 2
            screenHeight = (screenHeight / 2) * 2

            imageReader?.close()
            virtualDisplay?.release()

            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            
            // Dedicated background handler prevents main UI thread starvation
            val handler = backgroundHandler ?: Handler(Looper.getMainLooper())
            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val img = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    processAndCacheImage(img)
                } catch (_: Exception) {}
            }, handler)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "CaptchaScreenCaptureDisplay",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                handler
            )

            _isCapturing.value = true
            Logger.log("VISION", "Live Screen GPU Framebuffer active (${screenWidth}x${screenHeight}, ${screenDensity}dpi). Ready.", LogLevel.VISION)

        } catch (e: Throwable) {
            Logger.log("VISION", "MediaProjection initialization error: ${e.javaClass.simpleName} - ${e.message}", LogLevel.ERROR)
            _isCapturing.value = false
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
                latestFrameBitmap = finalBitmap
            }
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }

    /**
     * Captures an instant frame directly from the physical GPU virtual display
     */
    fun captureFrame(): Bitmap? {
        val reader = imageReader ?: return null
        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image != null) {
                processAndCacheImage(image)
            }
        } catch (e: Exception) {
            Logger.log("VISION", "Frame acquisition exception: ${e.message}", LogLevel.INFO)
        } finally {
            image?.close()
        }

        synchronized(frameLock) {
            return latestFrameBitmap?.let { Bitmap.createBitmap(it) }
        }
    }

    private fun cleanup() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (_: Exception) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        synchronized(frameLock) {
            latestFrameBitmap = null
        }
        _isCapturing.value = false
    }

    override fun onDestroy() {
        cleanup()
        try {
            captureThread?.quitSafely()
        } catch (_: Exception) {}
        captureThread = null
        backgroundHandler = null
        instance = null
        super.onDestroy()
        Logger.log("VISION", "ScreenCaptureService destroyed.", LogLevel.INFO)
    }
}