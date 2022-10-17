package com.mark.ocrscan

import android.hardware.Camera
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.nio.ByteBuffer

class TextRecognitionProcessor {
    private var recognizer: TextRecognizer? = null

    init {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun recognizeCreditCard(inputImage: InputImage, callback: OnCreditCardRecognitionCallback) {
        startRecognize(inputImage, callback)
    }

    fun stop() {
        recognizer?.close()
        recognizer = null
    }

    private fun <T> startRecognize(inputImage: InputImage, callback: OnTextRecognitionCallback<T>) {
        recognizer
            ?.process(inputImage)
            ?.addOnSuccessListener {
                handleResult(it, callback)
            }
            ?.addOnFailureListener {
                callback.onFailure(it)
            }
    }

    private fun <T> handleResult(text: Text, callback: OnTextRecognitionCallback<T>) {
        when (callback) {
            is OnCreditCardRecognitionCallback -> handleCreditCardCallback(text, callback)
        }
    }

    private fun handleCreditCardCallback(text: Text, callback: OnCreditCardRecognitionCallback) {
        for (textBlock in text.textBlocks) {
            val formatCardText = textBlock.lines.find {
                it.text.matches(Regex("[A-Za-z0-9]{4} [A-Za-z0-9]{4} [A-Za-z0-9]{4} [A-Za-z0-9]{4}"))
            }

            val formatDateText = textBlock.lines.find {
                it.text.matches(Regex("[A-Za-z0-9]{2}/[A-Za-z0-9]{2}"))
            }

            var creditCardNumber = ""
            var creditCardDate = ""
            if (formatCardText != null) {
                creditCardNumber = replaceRelativeLetterToNumber(formatCardText.text)
            }

            if (formatDateText != null) {
                creditCardDate = replaceRelativeLetterToNumber(formatDateText.text)
            }

            if (creditCardNumber.matches(Regex("[0-9]{4} [0-9]{4} [0-9]{4} [0-9]{4}"))) {
                val isDateFormat = creditCardDate.matches(Regex("[0-1][0-9]/[0-3][0-9]"))
                callback.onSuccess(CreditCardInfo(creditCardNumber, if (isDateFormat) creditCardDate else ""))
            }
        }
    }

    private fun replaceRelativeLetterToNumber(text: String) =
        text.replace("D", "0")
            .replace("C", "0")
            .replace("p", "0")
            .replace("L", "1")
            .replace("E", "2")
            .replace("e", "2")
            .replace("H", "4")
            .replace("b", "6")

    fun createInputImageByByteBuffer(byteBuffer: ByteBuffer?, size: Camera.Size?, rotationDegree: Int): InputImage? {
        if (byteBuffer == null) return null
        if (size == null) return null

        return InputImage.fromByteBuffer(byteBuffer, size.width, size.height, rotationDegree, InputImage.IMAGE_FORMAT_NV21)
    }

    interface OnTextRecognitionCallback<T> {
        fun onSuccess(result: T)
        fun onFailure(e: Exception)
    }

    interface OnCreditCardRecognitionCallback : OnTextRecognitionCallback<CreditCardInfo>
}