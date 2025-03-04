package com.example.ocrpassport.component.sdk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc
import kotlin.math.pow

object ImageProcessor {
    private fun flipImage(bitmap: Bitmap, horizontal: Boolean = true): Bitmap {
        val matrix = Matrix().apply {
            preScale(if (horizontal) -1.0f else 1.0f, 1.0f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    fun formatImageRealTime(imageProxy: ImageProxy): Bitmap {

        val rotatedBitmap: Bitmap
//
        val bitmap = imageProxyToBitmap(imageProxy)
        val flippedBitmap = flipImage(bitmap, horizontal = true)

        rotatedBitmap = if (ModelPhone.isEDCorPhone()){
            rotateImage(flippedBitmap, clockwise = true, isEDC = true ) // EDC
        } else {
            rotateImage(bitmap, clockwise = true, isEDC = false ) // Phone
        }
//
        val sharpenedBitmap = applySharpnessFilter(rotatedBitmap)
        return sharpenedBitmap
    }
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
            100,
            out
        )
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    fun rotateImage(bitmap: Bitmap, clockwise: Boolean = true, isEDC: Boolean): Bitmap {
        val matrix = Matrix()
        val angle: Float = if (isEDC){
            if (clockwise) -90f else 90f // EDC
        } else {
            if (clockwise) 90f else -90f // Phone
        }
        matrix.postRotate(angle)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun applySharpnessFilter(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val paint = Paint()
        val matrix = ColorMatrix()

        matrix.setSaturation(1f)
        paint.colorFilter = ColorMatrixColorFilter(matrix)

        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return resultBitmap
    }

    fun isPassportInFrame(bitmap: Bitmap?): Boolean {
        if (bitmap == null) {
            Log.e("ImageProcessor", "isPassportInFrame : Bitmap is null")
            return false
        }

        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

        Imgproc.GaussianBlur(grayMat, grayMat, org.opencv.core.Size(5.0, 5.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(grayMat, edges, 75.0, 200.0)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edges,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        for (contour in contours) {
            val approx = MatOfPoint2f()
            val contour2f = MatOfPoint2f(*contour.toArray())
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * Imgproc.arcLength(contour2f, true), true)

            if (approx.total() == 4L) {
                val rect = Imgproc.boundingRect(MatOfPoint(*approx.toArray()))

                val aspectRatio = rect.width.toDouble() / rect.height.toDouble()
                if (aspectRatio in 1.4..1.6) {
                    mat.release()
                    grayMat.release()
                    edges.release()
                    hierarchy.release()
                    return true
                }
            }
        }

        mat.release()
        grayMat.release()
        edges.release()
        hierarchy.release()

        return false
    }
    fun isImageSharp(bitmap: Bitmap, threshold: Double = 100.0): Boolean {

        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

        val laplacianMat = Mat()
        Imgproc.Laplacian(grayMat, laplacianMat, CvType.CV_64F)

        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        Core.meanStdDev(laplacianMat, mean, stddev)

        val variance = stddev.toArray()[0].pow(2)
        return variance > threshold
    }

}