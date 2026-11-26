
package com.example.poolaimassist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val CHANNEL_ID = "pool_aim_assist_channel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "PoolAimAssist"
    }

    private var mediaProjection: MediaProjection? = null
    private var captureEngine: CaptureEngine? = null
    private var overlayView: OverlayView? = null
    private var controlPanel: ControlPanelView? = null
    private var calibrationOverlay: CalibrationOverlay? = null
    private var windowManager: WindowManager? = null
    private val detector = BallDetector()
    private var calibrating = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            createNotificationChannel()
            val notification = buildNotification("Starting overlay controls…")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
            val resultData: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(EXTRA_RESULT_DATA)
            }
            if (resultCode != android.app.Activity.RESULT_OK || resultData == null) {
                throw IllegalStateException("Screen capture permission data was missing")
            }

            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // Add the touchable control panel first. If optional screen drawing/capture
            // fails later, the user still gets a visible Stop/Calibrate panel.
            addControlPanel()
            updateRunningNotification("Floating controls are active")

            // The guide layer is optional; it must not prevent the control panel appearing.
            runCatching { addOverlayWindow() }
                .onFailure { Log.e(TAG, "Guide overlay could not be added", it) }

            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = manager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("Android did not create MediaProjection")
            val projection = mediaProjection!!

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "Screen capture stopped by Android/user")
                    stopSelf()
                }
            }, Handler(Looper.getMainLooper()))

            // Do not cover the game with the full-screen calibration layer automatically.
            // The user can tap Calibrate on the floating panel when ready.
            runCatching {
                captureEngine = CaptureEngine(this, projection) { bitmap -> processFrame(bitmap) }
                captureEngine?.start()
            }.onFailure {
                Log.e(TAG, "Screen capture engine could not start", it)
                updateRunningNotification("Controls active; capture error: ${it.javaClass.simpleName}")
            }

            Log.i(TAG, "Aim Assist service started; floating panel added")
            return START_NOT_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Service startup failed", e)
            saveStartupStatus("START FAILED: ${e.javaClass.simpleName}: ${e.message ?: "no details"}")
            showFailureNotification(e)
            stopSelf()
            return START_NOT_STICKY
        }
    }

    private fun processFrame(bitmap: android.graphics.Bitmap) {
        try {
            if (calibrating) return

            val balls = detector.detectBalls(bitmap)
            val cue = balls.firstOrNull { it.isCueBall }
            val objects = balls.filterNot { it.isCueBall }
            val pockets = CalibrationStore.loadPockets(this)

            val guides = if (cue != null && pockets.isNotEmpty()) {
                TrajectoryCalculator.computeShotGuides(
                    cue,
                    objects,
                    pockets
                )
            } else {
                emptyList()
            }

            overlayView?.setPockets(pockets)
            overlayView?.update(cue, guides)

        } catch (e: Exception) {
            Log.e(TAG, "Frame processing failed", e)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun addOverlayWindow() {
        val wm = windowManager ?: return
        val view = OverlayView(this)
        overlayView = view

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        wm.addView(view, params)
    }

    private fun addControlPanel() {
        val wm = windowManager ?: return

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 24
        params.y = 120

        val panel = ControlPanelView(
            this,
            wm,
            params,
            onCalibrate = { startCalibration() },
            onStop = { stopSelf() }
        )

        controlPanel = panel
        wm.addView(panel, params)
    }

    private fun startCalibration() {
        if (calibrating) return

        val wm = windowManager ?: return
        calibrating = true

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START

        val calibration = CalibrationOverlay(this) { pockets ->
            CalibrationStore.savePockets(this, pockets)
            stopCalibration()
        }

        calibrationOverlay = calibration
        wm.addView(calibration, params)
    }

    private fun stopCalibration() {
        calibrating = false

        calibrationOverlay?.let {
            runCatching { windowManager?.removeView(it) }
        }

        calibrationOverlay = null
        overlayView?.setPockets(CalibrationStore.loadPockets(this))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pool Aim Assist",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String = "Showing controls over the game"): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pool Aim Assist")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun updateRunningNotification(message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message))
        saveStartupStatus(message)
    }

    private fun showFailureNotification(error: Exception) {
        val message = "${error.javaClass.simpleName}: ${error.message ?: "No details"}"
        runCatching {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID + 1,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Pool Aim Assist could not start")
                    .setContentText(message.take(180))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setAutoCancel(true)
                    .build()
            )
        }
    }

    private fun saveStartupStatus(message: String) {
        getSharedPreferences("aim_assist_status", Context.MODE_PRIVATE)
            .edit().putString("last_status", message).apply()
    }

    override fun onDestroy() {
        captureEngine?.stop()

        overlayView?.let {
            runCatching { windowManager?.removeView(it) }
        }

        controlPanel?.let {
            runCatching { windowManager?.removeView(it) }
        }

        calibrationOverlay?.let {
            runCatching { windowManager?.removeView(it) }
        }

        mediaProjection?.stop()

        super.onDestroy()
    }
}
