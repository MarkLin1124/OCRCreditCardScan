package com.mark.ocrscan

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.Camera
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CameraSource {
    private var preview: CameraPreview? = null
    private var camera: Camera? = null
    private var cameraSizePair: SizePair? = null

    //only need handle camera byteArray have to use these params
    private val bytesToByteBuffer = IdentityHashMap<ByteArray, ByteBuffer>()
    private var processingThread: Thread? = null
    private var frameProcessingRunnable: FrameProcessingRunnable? = null
    private var callback: OnCameraFrameCallback? = null

    fun init(context: Context) = init(context, null)

    fun init(context: Context, callback: OnCameraFrameCallback?) {
        camera = try {
            Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
        } catch (e: Exception) {
            null
        }

        camera?.let {
            cameraSizePair = CameraUtils.getCorrectSizePair(it)
            preview = CameraPreview(context, it)

            it.parameters = it.parameters?.apply {
                cameraSizePair?.let { sizePair ->
                    setPictureSize(sizePair.picture.width, sizePair.picture.height)
                    setPreviewSize(sizePair.preview.width, sizePair.preview.height)
                }

                previewFormat = ImageFormat.NV21
                focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            }
        }

        if (callback != null) {
            this.callback = callback
            startHandleCameraFrame()
        }
    }

    private fun startHandleCameraFrame() {
        preview?.setOnCameraReadyLister(object : OnCameraReadyListener {
            override fun onCameraReady(isReady: Boolean) {
                if (isReady) {
                    camera?.addCallbackBuffer(CameraUtils.createPreviewBuffer(cameraSizePair?.preview, bytesToByteBuffer))
                    camera?.addCallbackBuffer(CameraUtils.createPreviewBuffer(cameraSizePair?.preview, bytesToByteBuffer))
                    camera?.addCallbackBuffer(CameraUtils.createPreviewBuffer(cameraSizePair?.preview, bytesToByteBuffer))
                    camera?.addCallbackBuffer(CameraUtils.createPreviewBuffer(cameraSizePair?.preview, bytesToByteBuffer))


                    camera?.setPreviewCallbackWithBuffer { byteArray, camera ->
                        frameProcessingRunnable?.setNextFrame(byteArray, camera)
                    }
                }
            }
        })

        frameProcessingRunnable = FrameProcessingRunnable()
        processingThread = Thread(frameProcessingRunnable)
        frameProcessingRunnable?.setActive(true)
        processingThread?.start()
    }

    fun getCameraPreview() = preview

    fun getCamera() = camera

    fun getCameraSizePair() = cameraSizePair

    fun unInit() {
        frameProcessingRunnable?.setActive(false)
        processingThread?.join()
        processingThread = null

        camera?.stopPreview()
        camera?.setPreviewCallbackWithBuffer(null)
        camera?.setPreviewTexture(null)
        camera?.setPreviewDisplay(null)
        camera?.release()
        camera = null
        preview = null
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
                    data?.let {
                        callback?.onReceive(it)
                    }
                } finally {
                    camera?.addCallbackBuffer(data!!.array())
                }
            }
        }
    }

    interface OnCameraFrameCallback {
        fun onReceive(byteBuffer: ByteBuffer)
    }
}