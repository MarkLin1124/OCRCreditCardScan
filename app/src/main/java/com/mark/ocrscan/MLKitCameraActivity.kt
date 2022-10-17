package com.mark.ocrscan

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.nio.ByteBuffer

class MLKitCameraActivity : AppCompatActivity(), TextRecognitionProcessor.OnCreditCardRecognitionCallback, CameraSource.OnCameraFrameCallback {
    companion object {
        const val TAG = "MLKitCameraActivity"
        fun getActivityIntent(activity: AppCompatActivity) = Intent(activity, MLKitCameraActivity::class.java)
    }

    private val frameLayout by lazy {
        findViewById<FrameLayout>(R.id.layout_camera)
    }

    private var processor: TextRecognitionProcessor? = null
    private var cameraSource: CameraSource? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ml_kit)

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            init()
        } else {
            Toast.makeText(this, "No enable camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun init() {
        processor = TextRecognitionProcessor()

        cameraSource = CameraSource()
        cameraSource?.init(this, this)

        val preview = cameraSource?.getCameraPreview()
        preview?.let {
            frameLayout.addView(it)
        }
    }

    override fun onReceive(byteBuffer: ByteBuffer) {
        val sizePair = cameraSource?.getCameraSizePair()
        val preview = cameraSource?.getCameraPreview()
        val image = processor?.createInputImageByByteBuffer(byteBuffer, sizePair?.preview, preview?.rotationDegree ?: 0)
        image?.let {
            processor?.recognizeCreditCard(it, this@MLKitCameraActivity)
        }
    }

    override fun onSuccess(result: CreditCardInfo) {
        setResult(RESULT_OK, Intent().apply {
            putExtra("CardNumber", result.number)
        })
        finish()
    }

    override fun onFailure(e: Exception) {
        Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        processor?.stop()
        cameraSource?.unInit()

        super.onDestroy()
    }
}