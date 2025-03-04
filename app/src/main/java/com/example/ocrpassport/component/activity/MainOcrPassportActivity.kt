package com.example.ocrpassport.component.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.ocrpassport.component.models.MRZData
import com.example.ocrpassport.R
import com.example.ocrpassport.component.OCRPassportSDK
import com.example.ocrpassport.component.models.PersonDetails

class MainOcrPassportActivity : AppCompatActivity() {
    private lateinit var ocrPassportSDK: OCRPassportSDK

    private lateinit var realTimeBtn: Button

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

        realTimeBtn = findViewById(R.id.realTimeBtn)

        realTimeBtn.setOnClickListener {
            requestPermissionCameraRealTimeLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val intent = Intent(this, CameraPreviewActivity::class.java)
        activityResultLauncher.launch(intent)
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val mrzData = result.data?.getSerializableExtra("mrzData") as? MRZData
                if (mrzData != null) {
                    Log.d("activityResultLauncher", "mrzData: $mrzData")
                    startNfcReading(
                        mrzData.DocumentNumber.toString(),
                        mrzData.DateOfBirth.toString(),
                        mrzData.ExpiryDate.toString()
                    )
                }
            } else {
                startNfcReading("", "", "")
            }
        }

    private fun startNfcReading(passportNumber: String, birthDate: String, expiryDate: String) {
        val nfcIntent = Intent(this, NfcReadingActivity::class.java).apply {
            putExtra("passportNumber", passportNumber)
            putExtra("birthDate", birthDate)
            putExtra("expiryDate", expiryDate)
        }

        val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("อุปกรณ์ของคุณไม่รองรับ NFC")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        } else if (!nfcAdapter.isEnabled) {
            Toast.makeText(this, "กรุณาเปิดใช้งาน NFC", Toast.LENGTH_LONG).show()
        } else {
            activityNFCResult.launch(nfcIntent)
        }
    }

    private val activityNFCResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val mrzData = result.data?.getSerializableExtra("mrzData") as? PersonDetails
                if (mrzData != null) {
                    showMRZDialog(this, mrzData)
                }
            } else {
                showErrorDialog()
            }
        }


    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showMRZDialog(context: Context, mrzData: PersonDetails?) {
        val builder = AlertDialog.Builder(context)
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_mrz_data, null)

        val imageView = dialogView.findViewById<ImageView>(R.id.imageView)
        val textView = dialogView.findViewById<TextView>(R.id.textView)

        textView.text = mrzData.toString()
        imageView.setImageBitmap(mrzData!!.getPortraitImage())

        builder.setView(dialogView)
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun showErrorDialog() {
        val message = "Invalid data. Please scan again."
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("Try Again") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
