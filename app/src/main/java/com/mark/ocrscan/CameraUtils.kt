package com.mark.ocrscan

import android.graphics.*
import android.hardware.Camera
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

object CameraUtils {
    private fun generateValidPreviewSizeList(camera: Camera?): List<SizePair> {
        val validSizeList = mutableListOf<SizePair>()
        camera?.let {
            val params = it.parameters
            val supportedPreviewSizeList = params.supportedPreviewSizes
            val supportedPictureSizeList = params.supportedPictureSizes

            supportedPreviewSizeList.forEach { previewSize ->
                val previewAspectRatio = previewSize.width.toFloat() / previewSize.height.toFloat()

                for (pictureSize in supportedPictureSizeList) {
                    val pictureAspectRatio = pictureSize.width.toFloat() / pictureSize.height.toFloat()
                    if (abs(previewAspectRatio - pictureAspectRatio) < 0.01f) {
                        validSizeList.add(SizePair(previewSize, pictureSize))
                        break
                    }
                }
            }
        }

        return validSizeList
    }

    fun getCorrectSizePair(camera: Camera?): SizePair? {
        val desireWidth = 480
        val desireHeight = 360
        var correctSizePair: SizePair? = null

        camera?.let {
            val list = generateValidPreviewSizeList(it)
            var minDiff = Integer.MAX_VALUE
            for (sizePair in list) {
                val size = sizePair.preview
                val diff = abs(size.width - desireWidth) + abs(size.height - desireHeight)
                if (diff < minDiff) {
                    correctSizePair = sizePair
                    minDiff = diff
                }
            }
        }

        return correctSizePair
    }

    fun createPreviewBuffer(previewSize: Camera.Size?, bytesToByteBuffer: IdentityHashMap<ByteArray, ByteBuffer>): ByteArray {
        var bufferSize = 0
        previewSize?.let {
            val bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21)
            val sizeInBits = (it.height * it.width * bitsPerPixel).toLong()
            bufferSize = (ceil(sizeInBits / 8.0) + 1).roundToInt()
        }

        val byteArray = ByteArray(bufferSize)
        val byteBuffer = ByteBuffer.wrap(byteArray)

        bytesToByteBuffer[byteArray] = byteBuffer
        return byteArray
    }
}