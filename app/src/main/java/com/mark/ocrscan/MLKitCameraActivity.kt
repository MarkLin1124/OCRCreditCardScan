package com.mark.ocrscan

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class MLKitCameraActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MLKitCameraActivity"
        fun getActivityIntent(activity: AppCompatActivity) = Intent(activity, MLKitCameraActivity::class.java)
    }

    private var recognizer: TextRecognizer? = null

    private val frameLayout by lazy {
        findViewById<FrameLayout>(R.id.layout_camera)
    }

    private var camera: Camera? = null

    private var preview: CameraPreview? = null
    private val sizePair: SizePair? by lazy {
        CameraUtils.getCorrectSizePair(camera)
    }

    private val bytesToByteBuffer = IdentityHashMap<ByteArray, ByteBuffer>()
    private var processingThread: Thread? = null
    private var frameProcessingRunnable: FrameProcessingRunnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ml_kit)

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            initCamera()
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
            preview?.setOnCameraReadyLister(object : OnCameraReadyListener {
                override fun onCameraReady(isReady: Boolean) {
                    if (isReady) {
                        camera?.addCallbackBuffer(CameraUtils.createPreviewBuffer(sizePair?.preview, bytesToByteBuffer))
                        camera?.addCallbackBuffer(CameraUtils.createPreviewBuffer(sizePair?.preview, bytesToByteBuffer))
                        camera?.addCallbackBuffer(CameraUtils.createPreviewBuffer(sizePair?.preview, bytesToByteBuffer))
                        camera?.addCallbackBuffer(CameraUtils.createPreviewBuffer(sizePair?.preview, bytesToByteBuffer))


                        camera?.setPreviewCallbackWithBuffer { byteArray, camera ->
                            frameProcessingRunnable?.setNextFrame(byteArray, camera)
                        }
                    }
                }
            })
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

        frameProcessingRunnable = FrameProcessingRunnable()
        processingThread = Thread(frameProcessingRunnable)
        frameProcessingRunnable?.setActive(true)
        processingThread?.start()
    }

    private fun stopCamera() {
        frameProcessingRunnable?.setActive(false)
        processingThread?.join()
        processingThread = null

        camera?.stopPreview()
        camera?.setPreviewCallbackWithBuffer(null)
        camera?.setPreviewTexture(null)
        camera?.setPreviewDisplay(null)
        camera?.release()
        camera = null

        recognizer?.close()
        recognizer = null
    }

    private fun textRecognizerByByteArray(inputImage: InputImage) {
        recognizer?.process(inputImage)
            ?.addOnSuccessListener { visionText ->
                for (textBlock in visionText.textBlocks) {
                    val line = textBlock.lines.find {
                        it.text.matches(Regex("[A-Za-z0-9]{4} [A-Za-z0-9]{4} [A-Za-z0-9]{4} [A-Za-z0-9]{4}"))
                    }

                    if (line != null) {
                        val number = line.text
                            .replace("H", "4")
                            .replace("D", "0")
                            .replace("E", "2")
                            .replace("e", "2")
                            .replace("b", "6")
                            .replace("L", "1")
                            .replace("p", "0")

                        if (number.matches(Regex("[0-9]{4} [0-9]{4} [0-9]{4} [0-9]{4}"))) {
                            stopCamera()
                            setResult(RESULT_OK, Intent().apply {
                                putExtra("CardNumber", number)
                            })
                            finish()
                        }
                    }
                }
            }
            ?.addOnFailureListener { e ->
                Log.e("error", "e: ${e.message}")
            }
    }

    private inner class FrameProcessingRunnable : Runnable {
        private val lock = ReentrantLock()
        private val condition = lock.newCondition()

        private var active = true

        private var pendingFrameData: ByteBuffer? = null

        fun setActive(active: Boolean) {
            lock.withLock {
                this.active = active
                condition.signalAll()
            }
        }

        fun setNextFrame(data: ByteArray?, camera: Camera) {
            lock.withLock {
                if (pendingFrameData != null) {
                    camera.addCallbackBuffer(pendingFrameData!!.array())
                    pendingFrameData = null
                }
                if (!bytesToByteBuffer.containsKey(data)) {
                    return
                }
                pendingFrameData = bytesToByteBuffer.get(data)

                condition.signalAll()
            }
        }

        @SuppressLint("InlinedApi")
        override fun run() {
            var data: ByteBuffer?
            while (true) {
                lock.withLock {
                    while (active && (pendingFrameData == null)) {
                        try {
                            condition.await()
                        } catch (e: InterruptedException) {
                            return
                        }
                    }

                    if (!active) {
                        return
                    }

                    data = pendingFrameData
                    pendingFrameData = null
                }

                try {
                    val image = InputImage.fromByteBuffer(data!!, sizePair?.preview?.width ?: 0, sizePair?.preview?.height ?: 0, preview?.rotationDegree ?: 0, InputImage.IMAGE_FORMAT_NV21)
                    textRecognizerByByteArray(image)
                } finally {
                    camera?.addCallbackBuffer(data!!.array())
                }
            }
        }
    }
}