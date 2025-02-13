package com.example.ocrpassport.component


import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Base64
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ocrpassport.R
import net.sf.scuba.smartcards.CardService
import org.jmrtd.BACKey
import org.jmrtd.PassportService
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.icao.MRZInfo
import org.jmrtd.lds.iso19794.FaceImageInfo
import org.jmrtd.lds.iso19794.FaceInfo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream


class NfcReadingActivity : AppCompatActivity() {
    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var countdownTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_reading)
        countdownTextView = findViewById(R.id.countDown_RFID_Txt)

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
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null || !nfcAdapter.isEnabled) {
            Toast.makeText(this, "NFC is not available or enabled", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        countdownTimer.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {

            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            val isoDep = IsoDep.get(tag)

            tag?.let {
                Log.d("NfcReadingActivity", "NFC Tag detected!")
                Toast.makeText(this, "NFC Tag detected!", Toast.LENGTH_SHORT).show()
                readPassport(isoDep)
            }
        }
    }


    private fun readPassport(isoDep: IsoDep?) {
        try {
            if (isoDep == null) {
                Log.e("NfcReadingActivity", "Error: IsoDep is null")
                return
            }
            isoDep.connect()

            // ดึงข้อมูลที่ส่งมาจาก OCR หรือกรอกเอง
            val passportNumber = intent.getStringExtra("passportNumber") ?: return
            val birthDate = intent.getStringExtra("birthDate") ?: return
            val expiryDate = intent.getStringExtra("expiryDate") ?: return

            val bacKey = BACKey(passportNumber, birthDate, expiryDate)
            val cardService = CardService.getInstance(isoDep)
            val passportService = PassportService(cardService, PassportService.NORMAL_MAX_TRANCEIVE_LENGTH, PassportService.DEFAULT_MAX_BLOCKSIZE, true, false)
            passportService.open()

            // ใช้ BAC (Basic Access Control) สำหรับปลดล็อกการเข้าถึงข้อมูล
            passportService.sendSelectApplet(false)
            passportService.doBAC(bacKey)

            // อ่านข้อมูล DG1 (ข้อมูลส่วนบุคคล)
            val dg1InputStream: InputStream = passportService.getInputStream(PassportService.EF_DG1)
            val dg1File = DG1File(dg1InputStream)
            val mrzInfo: MRZInfo = dg1File.mrzInfo

            val dg2InputStream: InputStream = passportService.getInputStream(PassportService.EF_DG2)
            val dg2File = DG2File(dg2InputStream)
            val faceInfos = dg2File.faceInfos
            val allFaceImageInfos = mutableListOf<FaceImageInfo>()

            for (faceInfo in faceInfos) {
                allFaceImageInfos.addAll(faceInfo.faceImageInfos)
            }

            if (allFaceImageInfos.isNotEmpty()) {
                val faceImageInfo = allFaceImageInfos.first()

                val imageInputStream: InputStream = faceImageInfo.imageInputStream
                // ใช้การอ่าน InputStream ทีละบล็อกเพื่อให้มั่นใจว่าอ่านครบถ้วน
                val buffer = ByteArray(1024)
                val byteArrayOutputStream = ByteArrayOutputStream()

                var bytesRead: Int
                while (imageInputStream.read(buffer).also { bytesRead = it } != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead)
                }

                val imageBytes = byteArrayOutputStream.toByteArray()
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                Log.d("NfcReadingActivity", "bitmap: $bitmap")


            } else {
                Log.e("NfcReadingActivity", "No face image found in DG2")
            }

            Log.d("NfcReadingActivity", "Name: ${mrzInfo.secondaryIdentifier}, Surname: ${mrzInfo.primaryIdentifier}")
            Log.d("NfcReadingActivity", "Document No: ${mrzInfo.documentNumber}, Nationality: ${mrzInfo.nationality}")

            Toast.makeText(this, "Passport Read: ${mrzInfo.primaryIdentifier} ${mrzInfo.secondaryIdentifier}", Toast.LENGTH_LONG).show()

            isoDep.close()

        } catch (e: Exception) {
            Log.e("NfcReadingActivity", "Error reading passport: ${e.message}")
            e.printStackTrace()
        }
    }




    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val techListArray = arrayOf(arrayOf(android.nfc.tech.IsoDep::class.java.name))

        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, techListArray)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }

}