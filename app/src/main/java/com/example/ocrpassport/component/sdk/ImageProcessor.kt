package com.example.ocrpassport.component.sdk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
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
        // สร้าง Bitmap ใหม่ที่มีขนาดเหมือนเดิม
        val width = bitmap.width
        val height = bitmap.height
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // สร้าง Paint เพื่อใช้สำหรับการวาด
        val paint = Paint()
        val matrix = ColorMatrix()

        // กำหนดตัวกรองที่ทำให้ภาพคมขึ้น (ตัวเลขสามารถปรับได้ตามความเหมาะสม)
        matrix.setSaturation(1f)
        paint.colorFilter = ColorMatrixColorFilter(matrix)

        // สร้าง Canvas และวาดภาพบน Canvas ที่มี Paint
        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return resultBitmap
    }

    fun isPassportInFrame(bitmap: Bitmap?): Boolean {
        if (bitmap == null) {
            Log.e("ImageProcessor", "isPassportInFrame : Bitmap is null")
            return false
        }

        // แปลง Bitmap เป็น Mat
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // แปลงเป็น Grayscale
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

        // ใช้ GaussianBlur เพื่อลด Noise
        Imgproc.GaussianBlur(grayMat, grayMat, org.opencv.core.Size(5.0, 5.0), 0.0)

        // ใช้ Canny Edge Detection เพื่อหา edges
        val edges = Mat()
        Imgproc.Canny(grayMat, edges, 75.0, 200.0)

        // ค้นหา Contours
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
            // แปลง Contour เป็น ApproxPoly เพื่อหารูปทรงเรขาคณิต
            val approx = MatOfPoint2f()
            val contour2f = MatOfPoint2f(*contour.toArray())
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * Imgproc.arcLength(contour2f, true), true)

            // ตรวจสอบว่ามี 4 ด้าน (ลักษณะสี่เหลี่ยม)
            if (approx.total() == 4L) {
                // คำนวณ Bounding Box ของ Contour
                val rect = Imgproc.boundingRect(MatOfPoint(*approx.toArray()))

                // ตรวจสอบสัดส่วนของกรอบว่าใกล้เคียงกับพาสปอร์ตหรือไม่
                val aspectRatio = rect.width.toDouble() / rect.height.toDouble()
                if (aspectRatio in 1.4..1.6) { // Passport มีสัดส่วนประมาณ 1.41
                    // ปล่อยทรัพยากรและคืนค่า
                    mat.release()
                    grayMat.release()
                    edges.release()
                    hierarchy.release()
                    return true // พบ Passport ในกรอบ
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

        // Convert to grayscale
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

        // Compute Laplacian
        val laplacianMat = Mat()
        Imgproc.Laplacian(grayMat, laplacianMat, CvType.CV_64F)

        // Calculate mean and standard deviation
        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        Core.meanStdDev(laplacianMat, mean, stddev)

        // Get variance from standard deviation
        val variance = stddev.toArray()[0].pow(2)
        return variance > threshold
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}