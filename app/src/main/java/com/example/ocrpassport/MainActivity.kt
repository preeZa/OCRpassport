package com.example.ocrpassport

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.ocrpassport.component.activity.MainOcrPassportActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val ocrPassportActBtn = findViewById<Button>(R.id.ocrPassport_act_btn)
        ocrPassportActBtn.setOnClickListener {
            val Intent = Intent(this, MainOcrPassportActivity::class.java)
            startActivity(Intent)
        }

    }
}





