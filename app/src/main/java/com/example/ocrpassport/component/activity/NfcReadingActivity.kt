package com.example.ocrpassport.component.activity


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
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.example.ocrpassport.R
import com.example.ocrpassport.component.models.PersonDetails
import com.example.ocrpassport.component.util.DateUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.sf.scuba.smartcards.CardService
import org.jmrtd.BACKey
import org.jmrtd.PassportService
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.icao.MRZInfo
import org.jmrtd.lds.iso19794.FaceImageInfo

class NfcReadingActivity : AppCompatActivity() {
    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var countdownTextView: TextView
    private lateinit var lottieAnimation: LottieAnimationView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var nfcLinearLayout: LinearLayout
    private var bottomSheetDialog: BottomSheetDialog? = null
    private val resultIntent = Intent()
    private var personDetails: PersonDetails = PersonDetails()
    private var countdownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var isLoading: Boolean = false
        set(value) {
            field = value
            updateLoadingState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_reading)
        countdownTextView = findViewById(R.id.countDown_RFID_Txt)
        loadingLayout = findViewById(R.id.loadingLayout)
        lottieAnimation = findViewById(R.id.lottieAnimation)
        nfcLinearLayout = findViewById(R.id.nfcLinearLayout)
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
        dismissBottomSheet()
        isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isoDep == null) {
                    Log.e("NfcReadingActivity", "Error: IsoDep is null")
                    withContext(Dispatchers.Main) { finish() }
                    return@launch
                }

                withTimeout(5000) {
                    isoDep.connect()
                }

                val passportNumber = intent.getStringExtra("passportNumber")
                val birthDate = intent.getStringExtra("birthDate")
                val expiryDate = intent.getStringExtra("expiryDate")

                if (passportNumber.isNullOrEmpty() || birthDate.isNullOrEmpty() || expiryDate.isNullOrEmpty()) {
                    Log.e("NfcReadingActivity", "Missing passport data!")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@NfcReadingActivity, "ข้อมูลหนังสือเดินทางไม่ครบถ้วน", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    return@launch
                }

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

                try {
                    passportService.open()
                    passportService.sendSelectApplet(false)
                    passportService.doBAC(bacKey)

                    Log.e("passportService", "passportService : $passportService")

                    val dg1File = DG1File(passportService.getInputStream(PassportService.EF_DG1))
                    val mrzInfo: MRZInfo = dg1File.mrzInfo

                    personDetails.name = mrzInfo.secondaryIdentifier.replace("<", " ").trim()
                    personDetails.surname = mrzInfo.primaryIdentifier.replace("<", " ").trim()
                    personDetails.personalNumber = mrzInfo.personalNumber
                    personDetails.gender = mrzInfo.gender.toString()
                    personDetails.birthDate = DateUtil.convertFromMrzDate(mrzInfo.dateOfBirth)
                    personDetails.expiryDate = DateUtil.convertFromMrzDate(mrzInfo.dateOfExpiry)
                    personDetails.serialNumber = mrzInfo.documentNumber
                    personDetails.nationality = mrzInfo.nationality.replace("<", "")
                    personDetails.issuerAuthority = mrzInfo.issuingState.replace("<", "")

                    val dg2File = DG2File(passportService.getInputStream(PassportService.EF_DG2))

                    val allFaceImageInfos: MutableList<FaceImageInfo> = ArrayList()
                    for (faceInfo in dg2File.faceInfos) {
                        allFaceImageInfos.addAll(faceInfo.faceImageInfos)
                    }

                    if (allFaceImageInfos.isNotEmpty()) {
                        personDetails.faceImageInfo = allFaceImageInfos.first()
                    }

                    isoDep.close()
                    resultIntent.putExtra("mrzData", personDetails)

                    withContext(Dispatchers.Main) {
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }

                } catch (e: Exception) {
                    if (e.message?.contains("Failed response") == true) {
                        withContext(Dispatchers.Main) { finish() }
                    }
                    Log.e("NfcReadingActivity", "Error reading passport: ${e.message}")
                    e.printStackTrace()
                }

            } catch (e: TimeoutCancellationException) {
                Log.e("NfcReadingActivity", "NFC Connection Timeout")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NfcReadingActivity, "การเชื่อมต่อ NFC ใช้เวลานานเกินไป", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e("NfcReadingActivity", "Unexpected error: ${e.message}")
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }


    @SuppressLint("InflateParams")
    private fun showBottomSheetDialog(context: Context) {
        bottomSheetDialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_layout, null)
        bottomSheetDialog?.setContentView(view)

        if (!(context as? AppCompatActivity)?.isFinishing!!) {
            bottomSheetDialog?.show()
        }
    }
    private fun dismissBottomSheet() {
        bottomSheetDialog?.dismiss()
    }

    private fun startCountdown() {
        if (isTimerRunning) return

        isTimerRunning = true
        countdownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                runOnUiThread {
                    countdownTextView.alpha = 1f
                    countdownTextView.text = seconds.toString()
                }
            }

            override fun onFinish() {
                isTimerRunning = false
                finish()
            }
        }.start()
    }
    private fun updateLoadingState() {
        if (isLoading) {
            loadingLayout.visibility = View.VISIBLE
            lottieAnimation.playAnimation()
            nfcLinearLayout.visibility = View.GONE

        } else {
            lottieAnimation.cancelAnimation()
            loadingLayout.visibility = View.GONE
            nfcLinearLayout.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        startCountdown()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this) ?: return
        if (!nfcAdapter.isEnabled) {
            Toast.makeText(this, "Please enable NFC", Toast.LENGTH_LONG).show()
        }
        showBottomSheetDialog(this)
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val techList = arrayOf(arrayOf(IsoDep::class.java.name))
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, techList)

    }

    override fun onPause() {
        super.onPause()
        countdownTimer?.cancel()
        isTimerRunning = false
        if (::nfcAdapter.isInitialized) {
            nfcAdapter.disableForegroundDispatch(this)
        }
    }

}

