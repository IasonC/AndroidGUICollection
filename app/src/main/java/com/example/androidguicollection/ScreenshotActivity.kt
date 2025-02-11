//////////////////////////////////////////////////////////////////////////////
/// UNUSED for now -- cannot start Activity from Service after Android 10+ ///
//////////////////////////////////////////////////////////////////////////////

package com.example.androidguicollection

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.ContentValues
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.content.Intent

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.net.Uri
import java.util.Date
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.content.Context
import android.hardware.display.DisplayManager
import java.io.File
import java.io.FileOutputStream

class ScreenshotActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("onCreate SA", "ScreenshotActivity STARTED...")

        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(captureIntent, SCREEN_CAPTURE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("onActivityResult SA", "Callback for activity result in ScreenshotActivity")

        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)
            setupVirtualDisplay()
        }
    }

    private fun setupVirtualDisplay() {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2)
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                buffer.rewind()
                bitmap.copyPixelsFromBuffer(buffer)

                // Save the bitmap to a file
                saveImageToGallery(bitmap)

                image.close()
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Image_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, contentValues, null, null)
        }
    }

    companion object {
        // Static variables
        lateinit var mediaProjectionManager: MediaProjectionManager
        var mediaProjection: MediaProjection? = null
        var virtualDisplay: VirtualDisplay? = null
        var imageReader: ImageReader? = null
        const val SCREEN_CAPTURE_REQUEST_CODE = 1001
    }

}