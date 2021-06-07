package com.dany.cameraxexercise

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.dany.cameraxexercise.databinding.ActivityMainBinding
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.lang.Exception
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {
  private val cameraPermissionLauncer by lazy {
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { grant ->
      if (!grant) return@registerForActivityResult

      startCamera()
    }
  }

  private val permissionGranted: Boolean
    get() = ContextCompat.checkSelfPermission(
      applicationContext,
      Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

  private val cameraExecutor = Executors.newSingleThreadExecutor()

  // Get a stable reference of the modifiable image capture use case
  private var imageCapture: ImageCapture? = null
  private val outputDir by lazy {
    val mediaDir = externalMediaDirs.firstOrNull()?.let {
      File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
    }

    when {
      mediaDir != null && mediaDir.exists() -> mediaDir
      else -> filesDir
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main).run {
      when {
        permissionGranted -> startCamera()
        else -> cameraPermissionLauncer.launch(Manifest.permission.CAMERA)
      }

      setEventListener()
    }
  }

  private fun ActivityMainBinding.setEventListener() {
    btnCapture.setOnClickListener {
      // ImageCapture will be null if you tap the button before image capture is set up
      if (!permissionGranted || imageCapture == null) return@setOnClickListener

      // Create time-stamped output file to hold the image
      val photoFile = File(
        outputDir,
        SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
      )

      // Create output options object which contains file + metadata
      // where you can specify things about how you want your output to be
      val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

      // Set up image capture listener, which is triggered after photo has been taken
      imageCapture!!.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(this@MainActivity),
        object : ImageCapture.OnImageSavedCallback {
          override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            val savedUri = Uri.fromFile(photoFile)
            val msg = "Photo capture succeeded: $savedUri"
            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
            Log.d(TAG, msg)
          }

          override fun onError(exception: ImageCaptureException) {
            Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
          }
        }
      )
    }
  }

  private fun startCamera() {
    // used to bind the lifecycle of cameras to the lifecycle owner
    // this eliminates the task of opening and closing the camera since CameraX is lifecycle-aware
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

    cameraProviderFuture.addListener({
      // used to bind the lifecycle of your camera to the lifecycle owner within the application's process
      val cameraProvider = cameraProviderFuture.get()

      val preview = Preview.Builder()
        .build()
        .also {
          it.setSurfaceProvider(viewFinder.surfaceProvider)
        }

      imageCapture = ImageCapture.Builder().build()

      val imageAnalyzer = ImageAnalysis.Builder()
        .build()
        .also {
          it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
            Log.d(TAG, "Average luminosity : $luma")
          })
        }

      val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

      try {
        cameraProvider.unbindAll()

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
      } catch (e: Exception) {
      }

    }, ContextCompat.getMainExecutor(this))
  }

  private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {
    private fun ByteBuffer.toByteArray(): ByteArray {
      // rewind the buffer to zero
      rewind()
      val data = ByteArray(remaining())
      // copy the buffer into a byte array
      get(data)
      // Return the byte array
      return data
    }

    override fun analyze(image: ImageProxy) {
      val buffer = image.planes[0].buffer
      val data = buffer.toByteArray()
      val pixels = data.map { it.toInt() and 0xFF }
      val luma = pixels.average()

      listener(luma)

      image.close()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    cameraExecutor.shutdown()
  }

  companion object {
    private const val TAG = "CameraXBasic"
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
  }
}