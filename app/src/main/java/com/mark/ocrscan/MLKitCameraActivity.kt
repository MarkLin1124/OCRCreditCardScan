package com.mark.ocrscan

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MLKitCameraActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MLKitCameraActivity"
        fun getActivityIntent(activity: AppCompatActivity) = Intent(activity, MLKitCameraActivity::class.java)
    }

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val frameLayout by lazy {
        findViewById<FrameLayout>(R.id.layout_camera)
    }

    private var camera: Camera? = null

    private var dummySurfaceTexture: SurfaceTexture? = null
    private var preview: CameraPreview? = null
    private val sizePair: SizePair? by lazy {
        CameraUtils.getCorrectSizePair(camera)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ml_kit)

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            initCamera()
            Handler().postDelayed({
                camera?.addCallbackBuffer(CameraUtils.createPreviewBuffer(sizePair?.preview))
                camera?.addCallbackBuffer(CameraUtils.createPreviewBuffer(sizePair?.preview))
                camera?.addCallbackBuffer(CameraUtils.createPreviewBuffer(sizePair?.preview))
                camera?.addCallbackBuffer(CameraUtils.createPreviewBuffer(sizePair?.preview))

                camera?.setPreviewCallbackWithBuffer { byteArray, camera ->
                    camera.addCallbackBuffer(byteArray)
                    val image = InputImage.fromByteArray(byteArray, 480, 360, preview?.rotationDegree ?: 0, InputImage.IMAGE_FORMAT_NV21)
                    textRecognizerByByteArray(image)
                }

            }, 2000)
        } else {
            Toast.makeText(this, "", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initCamera() {
        camera = try {
            Camera.open(CameraInfo.CAMERA_FACING_BACK)
        } catch (e: Exception) {
            null
        }

        camera?.let {
            preview = CameraPreview(this, it)
        }

        camera?.parameters = camera?.parameters?.apply {
            sizePair?.let {
                setPictureSize(it.picture.width, it.picture.height)
                setPreviewSize(it.preview.width, it.preview.height)
            }

            previewFormat = ImageFormat.NV21
            focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
        }

        frameLayout?.addView(preview)
    }

    private fun stopCamera() {
        camera?.stopPreview()
        camera?.setPreviewCallbackWithBuffer(null)
        camera?.setPreviewTexture(null)
        dummySurfaceTexture = null
        camera?.setPreviewDisplay(null)
        camera?.release()
        camera = null
    }

    override fun onDestroy() {
        stopCamera()
        super.onDestroy()
    }

    private fun textRecognizerByByteArray(inputImage: InputImage) {
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                // Task completed successfully
                for (textBlock in visionText.textBlocks) {
                    val line = textBlock.lines.find {
                        it.text.matches(Regex("[0-9]{4} [0-9]{4} [0-9]{4} [0-9]{4}"))
                    }

                    if (line != null) {
                        stopCamera()
                        setResult(RESULT_OK, Intent().apply {
                            putExtra("CardNumber", line.text)
                        })
                        finish()
                    }
                }
            }
            .addOnFailureListener { e ->
                // Task failed with an exception
                Log.e("error", "e: ${e.message}")
            }
    }

    private fun textRecognizerByImageBitmap() {
//        val imageCard = findViewById<ImageView>(R.id.img_card)
//        val image = InputImage.fromBitmap(imageCard.drawable.toBitmap(), 0)
//        recognizer.process(image)
//            .addOnSuccessListener { visionText ->
//                // Task completed successfully
//                Log.d(TAG, "Text is: ${visionText.text}")
//            }
//            .addOnFailureListener { e ->
//                // Task failed with an exception
//                Log.e("error", "e: ${e.message}")
//            }
    }
}