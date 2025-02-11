package com.example.androidguicollection

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.IBinder
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ScreenCaptureService : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var imageReader: ImageReader

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("onStartCommand", "Screen Capture Service started...")
        // Start the service in the foreground
        startForeground(1, createNotification())

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_OK) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        val uuidString = intent.getStringExtra("uuidString") ?: return START_NOT_STICKY

        // Initialize MediaProjectionManager
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        startCapture(uuidString)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "screen_capture_channel"
            val channelName = "Screen Capture Service Channel"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        return Notification.Builder(this, "screen_capture_channel")
            .setContentTitle("Screen Capture")
            .setContentText("Screen capture is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startCapture(uuidString: String) {
        Log.d("ScreenCaptureService", "startCapture fun on screen with MediaProjection API")

        // Setup DisplayMetrics
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val screenDensity = displayMetrics.densityDpi
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Create ImageReader
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 3)

        // Create VirtualDisplay
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        imageReader.setOnImageAvailableListener({
            val image = imageReader.acquireLatestImage()
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            // Create Bitmap
            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Save Bitmap to File
            saveBitmap(bitmap, uuidString)

            image.close()
            imageReader?.close()
            virtualDisplay?.release()

            stopForeground(true)
            stopSelf()  // Stop service after capturing screenshot
        }, null)
    }

    private fun saveBitmap(bitmap: Bitmap, uuidString: String) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = uuidString + ".png" //"Screenshot_$timeStamp.png"
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DCIM), fileName)
            // Environment.DIRECTORY_DCIM saves to com.example.androidguicollection/files/DCIM (not passed to Gallery... TODO)
            // Environment.DIRECTORY_PICTURES saves to Internal Storage (/storage/emulated/0/) com.example.androidguicollection/files/Pictures

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d("ScreenCaptureService", "Screenshot saved: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("ScreenCaptureService", "Failed to save screenshot", e)
        }
    }

    companion object {
        private const val SCREEN_CAPTURE_REQUEST_CODE = 1000
        private const val CHANNEL_ID = "ScreenCaptureChannel"
    }
}