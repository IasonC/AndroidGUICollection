package com.example.androidguicollection
import com.example.androidguicollection.ScreenCaptureService

import android.os.Bundle
import android.os.Environment
import android.Manifest

import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.content.res.Resources
import android.graphics.PixelFormat
import android.media.ImageReader
import android.util.DisplayMetrics
import android.view.Display
import android.view.PixelCopy
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.util.Log

import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Date

import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.media.MediaScannerConnection
import java.io.OutputStream

import android.os.Handler
import android.os.Looper

import android.view.WindowManager
import android.content.BroadcastReceiver
import android.content.SharedPreferences


/*class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}*/

class MainActivity : AppCompatActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var resultCodeMedia: Int = 0
    private var dataMedia: Intent? = null
    private var mediaProjection: MediaProjection? = null

    private val sharedPreferences: SharedPreferences by lazy {
        getSharedPreferences("ScreenCapturePrefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 100
        private const val SCREEN_CAPTURE_REQUEST_CODE = 101

        /*fun getMediaProjectionManager(context: Context): MediaProjectionManager {
            return context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        }*/
    }

    object MediaProjectionHolder {
        // holds MediaProj instance tied to accepted permissions
        var mediaProjection: MediaProjection? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //enableEdgeToEdge()

        val editor = getSharedPreferences("ScreenCapturePrefs", Context.MODE_PRIVATE).edit()
        editor.putBoolean("permissionGranted", false)
        editor.apply()

        // Open App to be tested
        //val appToTest : String = "com.spotify.music"
        //val launchIntent = packageManager.getLaunchIntentForPackage(appToTest)
        //startActivity(launchIntent)

        // Runtime Permissions for Storage Read/Write
        val storage: Button? = findViewById(R.id.storage)
        storage?.setOnClickListener {checkPermission(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            STORAGE_PERMISSION_CODE)
        }
        //checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, STORAGE_PERMISSION_CODE)

        // Get MediaProjection API Permissions
        val mediaproj: Button? = findViewById(R.id.mediaproj)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaproj?.setOnClickListener {
            Log.d("onCreate MAIN", "Requesting MediaProj API permissions for screenshot.")

            if (sharedPreferences.getBoolean("permissionGranted", false)) {
                val intent = Intent(this, ScreenCaptureService::class.java)
                intent.putExtra("resultCode", resultCodeMedia)
                intent.putExtra("data", dataMedia)
                startForegroundService(intent)
            } else {
                val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
                startActivityForResult(captureIntent, SCREEN_CAPTURE_REQUEST_CODE)
            }
        }

        // Open Spotify app from activity_main app
        val app_btn: Button? = findViewById(R.id.app_btn)
        app_btn?.setOnClickListener {
            appRoutine()
        }

        // Intent listener for broadcasts of ACTION_ACCESSIBILITY_EVENT
        val intentFilter = IntentFilter("com.example.androidguicollection.ACTION_ACCESSIBILITY_EVENT")
        registerReceiver(accessibilityEventReceiver, intentFilter) // run aER fun on broadcast


        /*ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }*/
    }

    fun appRoutine() {
        // Open App to be tested
        val appToTest : String = "com.google.android.youtube" //"com.spotify.music"
        val launchIntent = packageManager.getLaunchIntentForPackage(appToTest)
        startActivity(launchIntent)

        Handler(Looper.getMainLooper()).postDelayed({
            // On-Click Listener for Screenshot
            takeScreenshot(this) // take screenshot of activity_main xml app (this activity's view)
        },1000)
    }

    private val accessibilityEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("accessibilityEventReceiver", "Callback from AccessibilityEvent Broadcast")
            val uuidString = intent?.getStringExtra("uuidString") // uuidString name for screenshot

            // take screenshot of currently-visible app & get bbox of visible interactables
            if (sharedPreferences.getBoolean("permissionGranted", false)) {
                // SCREENSHOT
                if (resultCodeMedia == Activity.RESULT_OK && dataMedia != null) {
                    val intent = Intent(this@MainActivity, ScreenCaptureService::class.java)
                    intent.putExtra("resultCode", resultCodeMedia)
                    intent.putExtra("data", dataMedia)
                    intent.putExtra("uuidString", uuidString)
                    startForegroundService(intent)
                } else {
                    Log.w("accessibilityEventReceiver",
                        "Wrong resultCode and/or null data for Screenshot with MediaProjection API")
                }

                // INTERACTABLES BBOX (served in MyAccessibilityService?)

            } else {
                Log.w("accessibilityEventReceiver",
                    "Tried to take screenshot with MediaProjection API without permissions.")
            }
        }
    }

    // Function to check and request permission.
    private fun checkPermission(permission: String, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this@MainActivity, permission) == PackageManager.PERMISSION_DENIED) {

            // Requesting the permission
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(permission), requestCode)
        } else {
            Log.d("checkPermission", "permission granted for storage.")
            Toast.makeText(this@MainActivity, "Storage Permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    // Func called when user accepts STORAGE permission
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d("onRequestPermissionResult", "Func callback after request permissions")

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this@MainActivity, "Storage Permission Granted", Toast.LENGTH_SHORT).show()
                takeScreenshot(findViewById(android.R.id.content))
            } else {
                Toast.makeText(this@MainActivity, "Storage Permission Denied", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this@MainActivity, "Wrong requestCode...", Toast.LENGTH_SHORT).show()
        }
    }

    fun takeScreenshot(activity: Activity)/*: File*/ {

        Log.d("takeScreenshot", "Screenshot func called.")

        val now = Date()
        val date = android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", now)

        val v1 = activity.window.decorView.rootView
        v1.isDrawingCacheEnabled = true
        val bitmap = Bitmap.createBitmap(v1.drawingCache)
        v1.isDrawingCacheEnabled = false

        saveImageToGallery(bitmap)

        //val imageFile = File(activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
        //    "screenshot-$date.png")
        //val imageFile: String = context.cacheDir.absolutePath + "/temp_${System.currentTimeMillis()}" + ".png"

        /*val outputStream = FileOutputStream(imageFile)
        val quality = 100
        bitmap.compress(Bitmap.CompressFormat.PNG, quality, outputStream)
        outputStream.flush()
        outputStream.close()*/

        //return imageFile
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("onActivityResult", "RETURN requestCode=$requestCode, resultCode=$resultCode, data=$data")
        Log.d("onActivityResult", "DESIRE requestCode=$SCREEN_CAPTURE_REQUEST_CODE, resultCode=${Activity.RESULT_OK}")

        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Log.d("onActivityResult", "Handling permission for Media Projection API")

            resultCodeMedia = resultCode
            dataMedia = data

            val intent = Intent(this, ScreenCaptureService::class.java)
            intent.putExtra("resultCode", resultCode)
            intent.putExtra("data", data)
            startForegroundService(intent)

            // Save permission granted status
            val sharedPreferences = getSharedPreferences("ScreenCapturePrefs", Context.MODE_PRIVATE)
            with(sharedPreferences.edit()) {
                putBoolean("permissionGranted", true)
                apply()
            }

            //val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            //val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            //Log.d("onActivityResult", "get mediaProjection object")
            //MediaProjectionHolder.mediaProjection = mediaProjection // Store MediaProjection instance
            //Log.d("onActivityResult", "save mediaProjection to Holder")

            /*Log.d("onActivityResult", "Handle result of request for MediaProj permissions.")
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)
            startScreenCapture(mediaProjection)*/
        }
        else {
            Log.d("onActivityResult", "FAILED case...")
        }
    }

    private fun startScreenCapture(mediaProjection: MediaProjection) {
        Log.d("startScreenCapture", "Get screenshot of current screen.")

        val metrics = Resources.getSystem().displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            // Create bitmap
            val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)

            // Save bitmap to file or use it as needed
            saveImageToGallery(bitmap)

            image.close()
        }, null)
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

    fun saveImage(
        context: Context,
        bmap: Bitmap,
        name: String
    ): File? {
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DCIM),
            "$name.jpeg"
        )

        val outStream: FileOutputStream
        try {
            outStream = FileOutputStream(file)
            bmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
            outStream.flush()
            outStream.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return file
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(accessibilityEventReceiver) // Unregister the receiver
    }

}
