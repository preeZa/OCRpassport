package com.example.ocrpassport.component.activity


import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ocrpassport.R
import com.example.ocrpassport.component.models.PersonDetails
import com.example.ocrpassport.component.util.DateUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import net.sf.scuba.smartcards.CardService
import org.jmrtd.BACKey
import org.jmrtd.PassportService
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.icao.MRZInfo
import org.jmrtd.lds.iso19794.FaceImageInfo
import java.io.InputStream

class NfcReadingActivity : AppCompatActivity() {
    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var countdownTextView: TextView
    private val resultIntent = Intent()
    private var personDetails: PersonDetails = PersonDetails()
    private lateinit var countdownTimer: CountDownTimer
    private var isTimerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_reading)
        countdownTextView = findViewById(R.id.countDown_RFID_Txt)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("NFC_DEBUG", "onNewIntent triggered with action: ${intent.action}")

        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {

            Log.d("NFC_DEBUG", "NFC Tag detected!")

            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
            Log.d("NFC_DEBUG", "Tag: $tag")

            val isoDep = IsoDep.get(tag)
            readPassport(isoDep)
        }
    }

    private fun readPassport(isoDep: IsoDep?) {
        try {
            if (isoDep == null) {
                Log.e("NfcReadingActivity", "Error: IsoDep is null")
                return
            }
            isoDep.connect()

            val passportNumber = intent.getStringExtra("passportNumber") ?: return
            val birthDate = intent.getStringExtra("birthDate") ?: return
            val expiryDate = intent.getStringExtra("expiryDate") ?: return

            Log.e("key", "passportNumber : $passportNumber , $birthDate , $expiryDate")

            val bacKey = BACKey(passportNumber, birthDate, expiryDate)
            val cardService = CardService.getInstance(isoDep)
            val passportService = PassportService(
                cardService,
                PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                PassportService.DEFAULT_MAX_BLOCKSIZE,
                true,
                false
            )
            passportService.open()

            passportService.sendSelectApplet(false)
            passportService.doBAC(bacKey)
            try {
                passportService.doBAC(bacKey)
            } catch (e: Exception) {
                if (e.message?.contains("Failed response") == true) {
                    finish()
                }
            }
            Log.e("passportService", "passportService : $passportService")

            val dg1InputStream: InputStream = passportService.getInputStream(PassportService.EF_DG1)
            val dg1File = DG1File(dg1InputStream)
            val mrzInfo: MRZInfo = dg1File.mrzInfo
            personDetails.name = mrzInfo.secondaryIdentifier.replace("<", " ").trim { it <= ' ' }
            personDetails.surname = mrzInfo.primaryIdentifier.replace("<", " ").trim { it <= ' ' }
            personDetails.personalNumber = mrzInfo.personalNumber
            personDetails.gender = mrzInfo.gender.toString()
            personDetails.birthDate = DateUtil.convertFromMrzDate(mrzInfo.dateOfBirth)
            personDetails.expiryDate = DateUtil.convertFromMrzDate(mrzInfo.dateOfExpiry)
            personDetails.serialNumber = mrzInfo.documentNumber
            personDetails.nationality = mrzInfo.nationality.replace("<", "")
            personDetails.issuerAuthority = mrzInfo.issuingState.replace("<", "")


            val dg2In = passportService.getInputStream(PassportService.EF_DG2)
            val dg2File = DG2File(dg2In)

            val faceInfos = dg2File.faceInfos
            val allFaceImageInfos: MutableList<FaceImageInfo> = ArrayList()
            for (faceInfo in faceInfos) {
                allFaceImageInfos.addAll(faceInfo.faceImageInfos)
            }
            if (allFaceImageInfos.isNotEmpty()) {
                val faceImageInfo = allFaceImageInfos.iterator().next()
                personDetails.faceImageInfo = faceImageInfo
            }

            Log.d(
                "NfcReadingActivity",
                "documentCode: ${mrzInfo.documentCode}, issuingState: ${mrzInfo.issuingState}"
            )
            Log.d(
                "NfcReadingActivity",
                "optionalData2: ${mrzInfo.optionalData2}, optionalData1: ${mrzInfo.optionalData1}"
            )

            isoDep.close()
            resultIntent.putExtra("mrzData", personDetails)
            setResult(RESULT_OK, resultIntent)
            finish()

        } catch (e: Exception) {
            Log.e("NfcReadingActivity", "Error reading passport: ${e.message}")
            e.printStackTrace()
        }
    }

    @SuppressLint("InflateParams")
    private fun showBottomSheetDialog(context: Context) {
        val bottomSheetDialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_layout, null)
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun startCountdown() {
        if (isTimerRunning) return

        isTimerRunning = true
        countdownTimer = object : CountDownTimer(60000, 1000) { // 60 วินาที
            @SuppressLint("SetTextI18n")
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()

                runOnUiThread {
                    ObjectAnimator.ofFloat(countdownTextView, "alpha", 0f, 1f).apply {
                        duration = 500
                        start()
                    }
                    countdownTextView.text = seconds.toString()
                }
            }

            override fun onFinish() {
                isTimerRunning = false
                finish()
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        startCountdown()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this) ?: return
        if (!nfcAdapter.isEnabled) {
            Toast.makeText(this, "Please enable NFC", Toast.LENGTH_LONG).show()
        }

        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val techList = arrayOf(arrayOf(IsoDep::class.java.name))
        showBottomSheetDialog(this)
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, techList)

    }

    override fun onPause() {
        super.onPause()
        countdownTimer.cancel()
        isTimerRunning = false
        if (::nfcAdapter.isInitialized) {
            nfcAdapter.disableForegroundDispatch(this)
        }
    }

}

