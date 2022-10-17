package com.mark.ocrscan

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class MLKitCameraActivity : AppCompatActivity(), TextRecognitionProcessor.OnCreditCardRecognitionCallback {
    companion object {
        const val TAG = "MLKitCameraActivity"
        fun getActivityIntent(activity: AppCompatActivity) = Intent(activity, MLKitCameraActivity::class.java)
    }

    private val frameLayout by lazy {
        findViewById<FrameLayout>(R.id.layout_camera)
    }

    private var processor: TextRecognitionProcessor? = null

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

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            init()
        } else {
            Toast.makeText(this, "No enable camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun init() {
        processor = TextRecognitionProcessor()
        initCamera()
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
        stopCamera()
        processor?.stop()

        super.onDestroy()
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
                    val image = processor?.createInputImageByByteBuffer(data!!, sizePair?.preview, preview?.rotationDegree ?: 0)
                    image?.let {
                        processor?.recognizeCreditCard(it, this@MLKitCameraActivity)
                    }
                } finally {
                    camera?.addCallbackBuffer(data!!.array())
                }
            }
        }
    }
}