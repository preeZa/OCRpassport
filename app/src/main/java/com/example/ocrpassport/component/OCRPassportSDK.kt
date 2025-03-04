package com.example.ocrpassport.component


import android.util.Log
import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.example.ocrpassport.component.models.MRZData
import com.example.ocrpassport.component.sdk.ImageProcessor
import com.example.ocrpassport.component.sdk.MRZUtils
import com.example.ocrpassport.component.sdk.OpenCVInitializer
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException


class OCRPassportSDK(private val context: Context) {


    private var mrzData: MRZData? = null

    init {
        OpenCVInitializer.initialize()
    }

    fun getMrzData(): MRZData? {
        return mrzData
    }


    fun getFormatImage(imageProxy: ImageProxy): Bitmap {
        return ImageProcessor.formatImageRealTime(imageProxy)
    }

    fun getIsImageSharp(bitmap: Bitmap, threshold: Double = 100.0): Boolean {
        return ImageProcessor.isImageSharp(bitmap, threshold)
    }

    suspend fun getIsPassport(rotatedBitmap: Bitmap): Boolean {
        if (ImageProcessor.isPassportInFrame(rotatedBitmap)) {
            val textAll = MRZUtils.textIsOrc(rotatedBitmap)
            return if (textAll.isNotEmpty()) {
                val detectedText = MRZUtils.detectedImageText(textAll)
                detectedText != "No valid MRZ lines found."
            } else {
                false
            }
        } else {
            return false
        }
    }

    suspend fun setMrz(bitmap: Bitmap) {
        try {
            withTimeout(10000) {
                mrzData = MRZUtils.recognizeText(bitmap)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e("OCRPassportSDK", "Timeout occurred while recognizing MRZ data", e)
        } catch (e: Exception) {
            Log.e("OCRPassportSDK", "Error recognizing MRZ data: ${e.message}", e)
        }
    }

}
