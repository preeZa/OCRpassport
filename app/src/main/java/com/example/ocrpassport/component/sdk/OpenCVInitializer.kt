package com.example.ocrpassport.component.sdk

import android.util.Log
import org.opencv.android.OpenCVLoader

object OpenCVInitializer {
    fun initialize() {
        try {
            System.loadLibrary("opencv_java4")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("OpenCV", "Failed to load library: ${e.message}")
        }

        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to initialize OpenCV")
        }
    }
}