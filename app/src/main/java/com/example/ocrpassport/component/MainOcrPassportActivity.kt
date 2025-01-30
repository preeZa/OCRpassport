package com.example.ocrpassport.component

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.example.ocrpassport.MRZData
import com.example.ocrpassport.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException


class MainOcrPassportActivity : AppCompatActivity() {
    private lateinit var ocrPassportSDK: OCRPassportSDK

    private lateinit var cameraImage: ImageView
    private lateinit var captureImgBtn: Button
    private lateinit var realTimeBtn: Button
    private lateinit var galleryImgBtn: Button
    private lateinit var loadingLayout: LinearLayout
    private lateinit var contentLayout: LinearLayout
    private lateinit var lottieAnimation: LottieAnimationView

    private lateinit var mrzData: MRZData

    private var isProcessing = false
    private var isCameraRealTime: Boolean = false
    private var isLoading: Boolean = false
        set(value) {
            field = value
            updateLoadingState()
        }

    private val requestPermissionCameraLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                createUriImage()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
            }
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                lifecycleScope.launch {
                    captureImage()
                }
            }
        }
    private val requestPermissionGallryLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                Toast.makeText(this, "Permission denied to access gallery", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    private val requestPermissionCameraRealTimeLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera premission denied", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_ocr_passport)
        ocrPassportSDK = OCRPassportSDK(this)

        cameraImage = findViewById(R.id.cameraImage)
        captureImgBtn = findViewById(R.id.captureImgBtn)
        realTimeBtn = findViewById(R.id.realTimeBtn)
        galleryImgBtn = findViewById(R.id.galleryImgBtn)
        loadingLayout = findViewById(R.id.loadingLayout)
        lottieAnimation = findViewById(R.id.lottieAnimation)
        contentLayout = findViewById(R.id.contentLayout)

        captureImgBtn.setOnClickListener {
            requestPermissionCameraLauncher.launch(Manifest.permission.CAMERA)
        }
        galleryImgBtn.setOnClickListener {
            requestPermissionGallryLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        realTimeBtn.setOnClickListener {
            requestPermissionCameraRealTimeLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun createUriImage() {
        val photoFile: File? = try {
            ocrPassportSDK.setCurrentPhotoPath()
        } catch (ex: IOException) {
            Toast.makeText(this, "Error occurred while creating the file", Toast.LENGTH_LONG).show()
            null
        }
        if (photoFile != null) {
            try {
                val photoUri: Uri =
                    FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", photoFile)
                takePictureLauncher.launch(photoUri)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to create file URI: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Error occurred while creating the file", Toast.LENGTH_LONG).show()
        }
    }

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    isLoading = true
                    withContext(Dispatchers.IO) {
                        ocrPassportSDK.setOcrPassportUri(uri)
                    }
                    mrzData = ocrPassportSDK.getMrzData()!!
                    println(mrzData.toString())
                    isLoading = false
                } catch (e: Exception) {
                    // Handle exception
                }
            }
        }
    }
    private suspend fun captureImage() {
        ocrPassportSDK.getCurrentPhotoPath()?.let { path ->
            try {
                isLoading = true
                ocrPassportSDK.setOcrPassportPath(path,1024,1024) // จะรอจนฟังก์ชันนี้ทำงานเสร็จ
                mrzData = ocrPassportSDK.getMrzData()!!
                println(mrzData.toString())
                isLoading = false
            } catch (e: Exception) {
                Log.e("GalleryImage", "Error processing image: ${e.message}", e)
                return // หากเกิดข้อผิดพลาดจะหยุดฟังก์ชันนี้
            }
        }

    }

    private fun startCamera() {
        val intent = Intent(this, camaraPreviewActivity::class.java)
        activityResultLauncher.launch(intent) // เปิดหน้าใหม่และรอผลลัพธ์
    }
    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val mrzDataReq = result.data?.getStringExtra("mrzData")
            val isInvalidData = mrzDataReq?.lines()?.all { it.trim().endsWith("=") || it.trim().isEmpty() } ?: true

            println("isInvalidDatav: $isInvalidData")
            println(mrzDataReq)
            if (isInvalidData) {
                val errorDialog = AlertDialog.Builder(this)
                errorDialog.setTitle("ข้อผิดพลาด")
                errorDialog.setMessage("ข้อมูลผิดพลาด โปรด Scan ใหม่")
                errorDialog.setPositiveButton("ลองอีกครั้ง") { dialog, _ ->
                    dialog.dismiss()
                }
                errorDialog.show()
            } else {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("MRZ Data")
                builder.setMessage(mrzDataReq)
                builder.setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                builder.show()
            }
        } else {
            val errorDialog = AlertDialog.Builder(this)
            errorDialog.setTitle("ข้อผิดพลาด")
            errorDialog.setMessage("ข้อมูลผิดพลาด โปรด Scan ใหม่")
            errorDialog.setPositiveButton("ลองอีกครั้ง") { dialog, _ ->
                dialog.dismiss()
            }
            errorDialog.show()
        }
    }
    private fun updateLoadingState() {
        if (isLoading) {
            loadingLayout.visibility = View.VISIBLE
            lottieAnimation.playAnimation()
            contentLayout.visibility = View.GONE

        } else {
            lottieAnimation.cancelAnimation() // หยุดแอนิเมชัน
            loadingLayout.visibility = View.GONE
            contentLayout.visibility = View.VISIBLE
        }
    }


}