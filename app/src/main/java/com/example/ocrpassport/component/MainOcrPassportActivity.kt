package com.example.ocrpassport.component

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainOcrPassportActivity : AppCompatActivity() {
    private lateinit var ocrPassportSDK: OCRPassportSDK

    private lateinit var captureImgBtn: Button
    private lateinit var realTimeBtn: Button
    private lateinit var galleryImgBtn: Button
    private lateinit var loadingLayout: LinearLayout
    private lateinit var contentLayout: LinearLayout
    private lateinit var lottieAnimation: LottieAnimationView

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
                showToast("Camera permission denied")
            }
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                lifecycleScope.launch { captureImage() }
            }
        }

    private val requestPermissionGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                showToast("Permission denied to access gallery")
            }
        }

    private val requestPermissionCameraRealTimeLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                showToast("Camera permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_ocr_passport)
        ocrPassportSDK = OCRPassportSDK(this)

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
            requestPermissionGalleryLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        realTimeBtn.setOnClickListener {
            requestPermissionCameraRealTimeLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun createUriImage() {
        val photoFile: File? = try {
            ocrPassportSDK.setCurrentPhotoPath()
        } catch (ex: IOException) {
            Log.e("OCRPassport", "Error creating file: ${ex.message}", ex)
            showToast("Error creating file: ${ex.message}")
            null
        }
        photoFile?.let {
            try {
                val photoUri: Uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", it)
                takePictureLauncher.launch(photoUri)
            } catch (e: Exception) {
                Log.e("OCRPassport", "Failed to create file URI: ${e.message}", e)
                showToast("Failed to create file URI: ${e.message}")
            }
        }
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { processImageUri(it) }
    }

    private fun processImageUri(uri: Uri) {
        lifecycleScope.launch {
            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    ocrPassportSDK.setOcrPassportUri(uri)
                    ocrPassportSDK.getMrzData()?.let {
                        // ใช้ mrzData ได้ตรงนี้
                    } ?: throw IOException("MRZ data is null")
                }
            } catch (e: IOException) {
                Log.e("OCRPassport", "File error: ${e.message}", e)
                showToast("File error: ${e.message}")
            } catch (e: java.util.concurrent.TimeoutException) {
                Log.e("OCRPassport", "Processing timeout: ${e.message}", e)
                showToast("Processing timeout, please try again")
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun captureImage() {
        ocrPassportSDK.getCurrentPhotoPath()?.let { path ->
            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    ocrPassportSDK.setOcrPassportPath(path, 1024, 1024)
                    ocrPassportSDK.getMrzData()?.let {
                        // ใช้ mrzData ได้ตรงนี้
                    } ?: throw IOException("MRZ data is null")
                }
            } catch (e: IOException) {
                Log.e("OCRPassport", "File error: ${e.message}", e)
                showToast("File error: ${e.message}")
            } catch (e: java.util.concurrent.TimeoutException) {
                Log.e("OCRPassport", "Processing timeout: ${e.message}", e)
                showToast("Processing timeout, please try again")
            } finally {
                isLoading = false
            }
        }
    }

    private fun startCamera() {
//        val intent = Intent(this, CameraPreviewActivity::class.java)
//        activityResultLauncher.launch(intent)
//        showBottomSheetDialog(this)
        startNfcReading("","","")
    }

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val mrzData = result.data?.getSerializableExtra("mrzData") as? MRZData
            if (mrzData != null) {
                Log.d("activityResultLauncher", "mrzData: $mrzData")
                startNfcReading(mrzData.DocumentNumber.toString(),mrzData.DateOfBirth.toString(),mrzData.ExpiryDate.toString())
            }
//            val isInvalidData = mrzDataReq?.lines()?.all { it.trim().endsWith("=") || it.trim().isEmpty() } ?: true

//            if (isInvalidData) {
//                showErrorDialog()
//            } else {
//                val builder = AlertDialog.Builder(this)
//                builder.setTitle("MRZ Data")
//                builder.setMessage(mrzDataReq)
//                builder.setPositiveButton("OK") { dialog, _ ->
//                    dialog.dismiss()
//                }
//                builder.show()
//            }
        } else {
            showErrorDialog()
        }
    }
    private fun startNfcReading(passportNumber: String, birthDate: String, expiryDate: String) {
        val nfcIntent = Intent(this, NfcReadingActivity::class.java).apply {
            putExtra("passportNumber", passportNumber)
            putExtra("birthDate", birthDate)
            putExtra("expiryDate", expiryDate)
        }
        startActivity(nfcIntent)
    }
    private fun showBottomSheetDialog(context: Context) {
        val bottomSheetDialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_layout, null)
        bottomSheetDialog.setContentView(view)

        val btnClose = view.findViewById<Button>(R.id.btnClose)
        btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showErrorDialog() {
        val message = "Invalid data. Please scan again."
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("Try Again") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun updateLoadingState() {
        if (isLoading) {
            loadingLayout.visibility = View.VISIBLE
            lottieAnimation.playAnimation()
            contentLayout.visibility = View.GONE
        } else {
            lottieAnimation.cancelAnimation()
            loadingLayout.visibility = View.GONE
            contentLayout.visibility = View.VISIBLE
        }
    }
}
