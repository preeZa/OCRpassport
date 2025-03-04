package com.example.ocrpassport.component.activity

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
import com.example.ocrpassport.component.models.MRZData
import com.example.ocrpassport.R
import com.example.ocrpassport.component.OCRPassportSDK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeoutException


class CameraPreviewActivity : AppCompatActivity() {
    private lateinit var ocrPassportSDK: OCRPassportSDK

    private lateinit var camaraLayout: LinearLayout
    private lateinit var previewView: PreviewView
    private lateinit var lottieAnimation: LottieAnimationView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var mrzData: MRZData
    private lateinit var countdownTextView: TextView


    private val resultIntent = Intent()

    private var isProcessing = false
    private var isCameraRealTime: Boolean = false
    private var isLoading: Boolean = false
        set(value) {
            field = value
            updateLoadingState()
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ocrPassportSDK = OCRPassportSDK(this)
        setContentView(R.layout.activity_camera_preview)

        val scanLine: View = findViewById(R.id.scan_line)
        val parentView: View =  findViewById(R.id.scan_frame)
        previewView = findViewById(R.id.previewView)
        loadingLayout = findViewById(R.id.loadingLayout)
        lottieAnimation = findViewById(R.id.lottieAnimation)
        camaraLayout = findViewById(R.id.camaraLayout)

        countdownTextView = findViewById(R.id.countdownTextView)

        val countdownTimer = object : CountDownTimer(10000, 1000) { // 10 วินาที
            @SuppressLint("SetTextI18n")
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                ObjectAnimator.ofFloat(countdownTextView, "alpha", 0f, 1f).apply {
                    duration = 500
                    start()
                }
                runOnUiThread {
                    countdownTextView.text = seconds.toString()
                }
            }

            override fun onFinish() {
                countdownTextView.text = ""
            }
        }
        parentView.post {
            val parentHeight = (parentView.height - (0.5 * 100)).toInt()
            val scanLineHeight = scanLine.height
            val maxTranslationY = parentHeight - scanLineHeight.toFloat()

            val animator = ObjectAnimator.ofFloat(scanLine, "translationY", 0f, maxTranslationY).apply {
                duration = 1000  // เพิ่มเวลาเพื่อให้เคลื่อนที่ต่อเนื่องขึ้น
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }
            animator.start()
        }
        countdownTimer.start()
        startCamera()

    }

    private fun startCamera() {

        val preview = Preview.Builder().build()
        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        var isReadyToAnalyze = false

        var isCameraStopped = false

        imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
            if (!isReadyToAnalyze || isCameraStopped) {
                imageProxy.close()
                return@setAnalyzer
            }

            if (isProcessing) {
                imageProxy.close()
                return@setAnalyzer
            }

            isProcessing = true
            try {
                val sharpenedBitmap = ocrPassportSDK.getFormatImage(imageProxy)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (ocrPassportSDK.getIsImageSharp(sharpenedBitmap)) {
                            withContext(Dispatchers.Main) {
                                if (ocrPassportSDK.getIsPassport(sharpenedBitmap)) {
                                    ocrPassportSDK.setMrz(sharpenedBitmap)
                                    isLoading = true
                                    delay(2000)

                                    mrzData = ocrPassportSDK.getMrzData()!!
                                    resultIntent.putExtra("mrzData", mrzData)
                                    setResult(RESULT_OK, resultIntent)

                                    isProcessing = false
                                    isCameraRealTime = false
                                    stopCamera()
                                } else {
                                    isProcessing = false
                                }
                            }
                        } else {
                            isProcessing = false
                        }
                    } catch (e: TimeoutException) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@CameraPreviewActivity, "Processing Timeout!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: IOException) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@CameraPreviewActivity, "Image processing failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        imageProxy.close()
                        isProcessing = false
                    }
                }
            } catch (e: Exception) {
                imageProxy.close()
                isProcessing = false
            }
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                preview.surfaceProvider = previewView.surfaceProvider
                Handler(mainLooper).postDelayed({
                    isReadyToAnalyze = true
                    Toast.makeText(this, "Start process image", Toast.LENGTH_SHORT).show()
                }, 10000)

                Handler(mainLooper).postDelayed({
                    isProcessing = false
                    isCameraRealTime = false
                    isCameraStopped = true  // หยุดการวิเคราะห์
                    setResult(RESULT_CANCELED, resultIntent)
                    stopCamera()
                }, 20000)

            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            cameraProvider.unbindAll()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            finish()
            isLoading = false
        }
    }
    private fun updateLoadingState() {
        if (isLoading) {
            loadingLayout.visibility = View.VISIBLE
            lottieAnimation.playAnimation()
            camaraLayout.visibility = View.GONE

        } else {
            lottieAnimation.cancelAnimation()
            loadingLayout.visibility = View.GONE
            camaraLayout.visibility = View.VISIBLE
        }
    }
}