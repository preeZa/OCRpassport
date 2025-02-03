package com.example.ocrpassport.component


import android.util.Log
import java.io.File
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.example.ocrpassport.MRZData
import com.example.ocrpassport.component.sdk.ImageProcessor
import com.example.ocrpassport.component.sdk.MRZUtils
import com.example.ocrpassport.component.sdk.OpenCVInitializer
import com.example.ocrpassport.component.sdk.modelPhone
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException


class OCRPassportSDK(private val context: Context) {

    private var currentPhotoPath: String? = null
    private var mrzData: MRZData? = null

    init {
        OpenCVInitializer.initialize(context)
    }

    fun setCurrentPhotoPath(): File {
        return ImageProcessor.createTempPhotoFile(context).also {
            currentPhotoPath = it.absolutePath
        }
    }

    fun getMrzData(): MRZData? {
        return mrzData
    }

    fun getCurrentPhotoPath(): String? {
        return currentPhotoPath
    }

    suspend fun setOcrPassportUri(uri: Uri) {
        try {
            withTimeout(5000) { // กำหนด timeout 5 วินาที
                val bitmap = ImageProcessor.uriToBitmap(context, uri)
                if (bitmap != null) {
                    setMrz(bitmap)
                } else {
                    Log.e("OCRPassportSDK", "Bitmap is null")
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e("OCRPassportSDK", "Timeout occurred while processing URI", e)
        } catch (e: Exception) {
            Log.e("OCRPassportSDK", "Error processing URI: ${e.message}", e)
        }
    }
    suspend fun setOcrPassportPath(path: String, reqWidth: Int, reqHeight: Int) {
        try {
            withTimeout(5000) { // Timeout 5 วินาที
                val bitmap = ImageProcessor.decodeSampledBitmapFromFile(path, reqWidth, reqHeight)
                val image: Bitmap

                if (modelPhone.isEDCorPhone()) {
                    image = ImageProcessor.flipImage(bitmap, horizontal = true)
                } else {
                    image = ImageProcessor.rotateImage(bitmap, clockwise = true, isEDC = false)
                }
                setMrz(image)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e("OCRPassportSDK", "Timeout occurred while processing file path", e)
        } catch (e: Exception) {
            Log.e("OCRPassportSDK", "Error processing file path: ${e.message}", e)
        }
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
            if (textAll.isNotEmpty()) {
                val detectedText = MRZUtils.detectedImageText(textAll)
                if (detectedText == "No valid MRZ lines found.") {
                    return false
                } else {
//                    setMrz(rotatedBitmap)
                    return true
                }
            }else {
                return false
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
