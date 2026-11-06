package com.zinsch.aircursor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot

class HandTrackingService : LifecycleService() {
    private val executor = Executors.newSingleThreadExecutor()
    private var landmarker: HandLandmarker? = null
    private var overlay: CursorOverlay? = null
    private var windowManager: WindowManager? = null
    private var analysis: ImageAnalysis? = null

    private var lastInference = 0L
    private var lastDispatch = 0L
    private var smoothX = -1f
    private var smoothY = -1f
    private var previousX = -1f
    private var previousY = -1f
    private var wasPinching = false
    private var dragMode = false
    private var pinchStart = 0L
    private var lastScrollY = -1f
    private var lastScrollAt = 0L
    private var lastGestureMode = "POINT"

    private var screenWidth = 1
    private var screenHeight = 1

    private val inferenceInterval: Long get() = 1000L / SettingsStore.inference(this).coerceIn(8, 24)
    private val dispatchInterval = 45L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1001, buildNotification())
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        createOverlay()
        initializeLandmarker()
        startCamera()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("aircursor_tracking", "AirCursor tracking", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, "aircursor_tracking")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("AirCursor aktif")
            .setContentText("Hand tracking berjalan di perangkat")
            .setOngoing(true)
            .build()

    private fun createOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager!!.defaultDisplay
        val size = Point()
        display.getRealSize(size)
        screenWidth = size.x
        screenHeight = size.y

        overlay = CursorOverlay(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager!!.addView(overlay, params)
    }

    private fun initializeLandmarker() {
        try {
            val base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.VIDEO)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.52f)
                .setMinHandPresenceConfidence(0.52f)
                .setMinTrackingConfidence(0.52f)
                .build()
            landmarker = HandLandmarker.createFromOptions(this, options)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val selector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            android.util.Size(480, 360),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
                        )
                    ).build()
                analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(executor) { image ->
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastInference < inferenceInterval) {
                                image.close()
                                return@setAnalyzer
                            }
                            lastInference = now
                            var bitmap: android.graphics.Bitmap? = null
                            var rotated: android.graphics.Bitmap? = null
                            try {
                                val buffer = image.planes[0].buffer
                                buffer.rewind()
                                bitmap = android.graphics.Bitmap.createBitmap(
                                    image.width, image.height, android.graphics.Bitmap.Config.ARGB_8888
                                )
                                bitmap.copyPixelsFromBuffer(buffer)
                                val input = if (image.imageInfo.rotationDegrees != 0) {
                                    val matrix = android.graphics.Matrix().apply {
                                        postRotate(image.imageInfo.rotationDegrees.toFloat())
                                    }
                                    android.graphics.Bitmap.createBitmap(
                                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                    ).also { rotated = it }
                                } else bitmap
                                val result = landmarker?.detectForVideo(
                                    BitmapImageBuilder(input).build(), SystemClock.uptimeMillis()
                                )
                                if (result != null) processResult(result)
                            } catch (_: Exception) {
                                // Drop a bad frame; KEEP_ONLY_LATEST prevents a backlog.
                            } finally {
                                bitmap?.recycle()
                                if (rotated !== bitmap) rotated?.recycle()
                                image.close()
                            }
                        }
                    }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (_: Exception) {
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processResult(result: HandLandmarkerResult) {
        if (result.landmarks().isEmpty()) {
            wasPinching = false
            if (dragMode) AirCursorAccessibilityService.instance?.endDrag()
            dragMode = false
            smoothX = -1f
            smoothY = -1f
            overlay?.postInvalidate()
            return
        }

        val hand = result.landmarks()[0]
        val index = hand[8]
        val thumb = hand[4]
        val middle = hand[12]
        val wrist = hand[0]

        val sensitivity = SettingsStore.sensitivity(this).coerceIn(0.5f, 1.5f)
        val nx = (1f - index.x()).coerceIn(0f, 1f)
        val ny = index.y().coerceIn(0f, 1f)
        val targetX = (screenWidth * (0.5f + (nx - 0.5f) * sensitivity)).coerceIn(4f, screenWidth - 4f)
        val targetY = (screenHeight * (0.5f + (ny - 0.5f) * sensitivity)).coerceIn(4f, screenHeight - 4f)
        val alpha = SettingsStore.smoothing(this).coerceIn(0.12f, 0.65f)
        smoothX = if (smoothX < 0f) targetX else smoothX + (targetX - smoothX) * alpha
        smoothY = if (smoothY < 0f) targetY else smoothY + (targetY - smoothY) * alpha

        val pinchDistance = hypot(index.x() - thumb.x(), index.y() - thumb.y())
        val pinch = if (wasPinching) pinchDistance < 0.075f else pinchDistance < 0.050f
        val twoFinger = SettingsStore.scrollEnabled(this) &&
            distanceToWrist(index.x(), index.y(), wrist.x(), wrist.y()) >
            distanceToWrist(middle.x(), middle.y(), wrist.x(), wrist.y()) * 0.92f &&
            distanceToWrist(middle.x(), middle.y(), wrist.x(), wrist.y()) > 0.20f &&
            !pinch

        val now = SystemClock.elapsedRealtime()
        val service = AirCursorAccessibilityService.instance
        var mode = "POINT"

        if (pinch && !wasPinching) {
            pinchStart = now
            dragMode = false
            mode = "PINCH"
        } else if (pinch && wasPinching && now - pinchStart > 260) {
            mode = "DRAG"
            if (!dragMode) {
                service?.beginDrag(smoothX, smoothY)
                dragMode = true
            } else if (now - lastDispatch >= dispatchInterval) {
                service?.moveDrag(smoothX, smoothY)
                lastDispatch = now
            }
        } else if (!pinch && wasPinching) {
            if (dragMode) service?.endDrag()
            else if (now - pinchStart < 260) service?.tap(smoothX, smoothY)
            dragMode = false
        } else if (twoFinger) {
            mode = "SCROLL"
            if (lastScrollY >= 0f && now - lastScrollAt > 300) {
                val delta = smoothY - lastScrollY
                if (abs(delta) > 65f) {
                    val direction = if (delta < 0) -1f else 1f
                    service?.scroll(smoothX, smoothY, smoothX, smoothY + direction * 240f)
                    lastScrollAt = now
                }
            }
            lastScrollY = smoothY
        } else {
            lastScrollY = -1f
        }

        wasPinching = pinch
        previousX = smoothX
        previousY = smoothY
        lastGestureMode = mode
        overlay?.post {
            overlay?.cursorX = smoothX
            overlay?.cursorY = smoothY
            overlay?.mode = mode
            overlay?.invalidate()
        }
    }

    private fun distanceToWrist(x: Float, y: Float, wx: Float, wy: Float) =
        hypot(x - wx, y - wy)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { analysis?.clearAnalyzer() } catch (_: Exception) {}
        try { ProcessCameraProvider.getInstance(this).get().unbindAll() } catch (_: Exception) {}
        try { landmarker?.close() } catch (_: Exception) {}
        try { overlay?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        executor.shutdown()
        super.onDestroy()
    }
}
