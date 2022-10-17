package com.mark.ocrscan

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Build
import android.view.*
import androidx.core.hardware.display.DisplayManagerCompat

class CameraPreview(context: Context, private val mCamera: Camera) : SurfaceView(context), SurfaceHolder.Callback {
    var rotationDegree = 0

    private val mHolder: SurfaceHolder = holder.apply {
        addCallback(this@CameraPreview)
    }

    private var onCameraReadyListener: OnCameraReadyListener? = null

    override fun surfaceCreated(holder: SurfaceHolder) {
        startCamera()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        if (mHolder.surface == null) {
            // preview surface does not exist
            return
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview()
            onCameraReadyListener?.onCameraReady(false)
        } catch (e: Exception) {
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here
        setCameraDisplayOrientation()

        // start preview with new settings
        startCamera()
    }

    private fun startCamera() {
        // start preview with new settings
        mCamera.apply {
            try {
                val dummySurfaceTexture = SurfaceTexture(100)
                setPreviewTexture(dummySurfaceTexture)
                setPreviewDisplay(holder)
                startPreview()

                onCameraReadyListener?.onCameraReady(true)
            } catch (e: Exception) {
            }
        }
    }

    fun setOnCameraReadyLister(listener: OnCameraReadyListener) {
        this.onCameraReadyListener = listener
    }

    private fun setCameraDisplayOrientation() {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation
        } else {
            DisplayManagerCompat.getInstance(context).getDisplay(Display.DEFAULT_DISPLAY)?.rotation
        }

        var degrees = 0

        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }

        val cameraInfo = Camera.CameraInfo()
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, cameraInfo)
        rotationDegree = (cameraInfo.orientation - degrees + 360) % 360

        try {
            mCamera.setDisplayOrientation(rotationDegree)
        } catch (e: Exception) {

        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
    }
}

interface OnCameraReadyListener {
    fun onCameraReady(isReady: Boolean)
}