package com.example.ocrpassport.component.sdk

import android.os.Build

object ModelPhone {

    fun isEDCorPhone(): Boolean {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        if (manufacturer.contains("p12", ignoreCase = true) ||
            model.contains("P12", ignoreCase = true) ||
            model.startsWith("EDC", ignoreCase = true)) {
            return true
        } else {
            return false // Phone
        }
    }
}