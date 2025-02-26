package com.example.ocrpassport.component.sdk

import android.graphics.Bitmap
import android.util.Log
import com.example.ocrpassport.MRZData
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object MRZUtils {
    suspend fun textIsOrc(bitmap: Bitmap): String {
        return withContext(Dispatchers.Default) {
            suspendCoroutine { continuation ->
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(inputImage)
                    .addOnSuccessListener { text ->
                        continuation.resume(text.text)
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                        Log.e("MRZUtils", "textIsOrc : ${e.message}")
                        continuation.resume("")
                    }
            }
        }
    }

    suspend fun recognizeText(bitmap: Bitmap): MRZData? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val orcText = recognizer.process(image).await()

            val detectedText = detectedImageText(orcText.text)

            if (detectedText == "No valid MRZ lines found.") {
                Log.e("MRZUtils", "Failed to recognize text: No valid MRZ lines found")
                null
            } else {
                val mrzData = convertToMRZData(detectedText)
                mrzData
            }
        } catch (e: Exception) {
            Log.e("MRZUtils", "Failed to recognize text: ${e.message}")
            null
        }
    }

    fun detectedImageText(text: String): String {
        return try {
            val lines = text.split("\n")

            val mrzPattern =
                Pattern.compile(".*[<>«].*")

            val mrzLines = mutableListOf<String>()
            for (line in lines) {
                if (mrzPattern.matcher(line).matches()) {
                    mrzLines.add(line)
                }
            }
            if (mrzLines.isNotEmpty()) {
                return mrzLines.joinToString("\n")
            } else {
                throw IllegalArgumentException("detectedImageText : mrzLines Empty")
                Log.e("MRZUtils", "detectedImageText : mrzLines Empty")
                return "No valid MRZ lines found."
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Error detectedImageText : ${e.message}")
            Log.e("MRZUtils", "Error detectedImageText : ${e.message}")
            return "No valid MRZ lines found."
        }
    }

    private fun formatDate(data: String, isExpirationDate: Boolean = false): String {
        try {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val currentYearShort = currentYear % 100
            val firstTwoDigits = currentYear / 100   // 20 (จาก 2025)
            val previousCentury = firstTwoDigits - 1 // 19 (ศตวรรษก่อนหน้า)
            val nextCentury = firstTwoDigits + 1     // 21 (ศตวรรษถัดไป)

            if (data.length != 6) {
                throw IllegalArgumentException("Invalid day: $data")
            }

            val birthYearPrefix = data.substring(0, 2).toInt()


            val yearPrefix = when {
                isExpirationDate -> {
                    // กรณีวันหมดอายุ (เช่น 99 ควรเป็น 2099)
                    if (birthYearPrefix >= currentYearShort) firstTwoDigits
                    else nextCentury
                }
                else -> {
                    // กรณีวันเกิด (เช่น 99 อาจเป็น 1999 หรือ 2099)
                    if (birthYearPrefix > currentYearShort) previousCentury
                    else firstTwoDigits
                }
            }
            val year = "$yearPrefix$birthYearPrefix"
            val month = data.substring(2, 4)
            val day = data.substring(4, 6)

            if (month.toInt() !in 1..12) {
                throw IllegalArgumentException("Invalid month: $month")
            }
            if (day.toInt() !in 1..31) {
                throw IllegalArgumentException("Invalid day: $day")
            }

            val formattedDate = "$year-$month-$day"
            return formattedDate

        } catch (e: StringIndexOutOfBoundsException) {
            Log.e("MRZUtils", "Incomplete data: ${e.message}")
            return ""
        } catch (e: NumberFormatException) {
            Log.e("MRZUtils", "Cannot convert data to number: ${e.message}")
            return ""
        } catch (e: IllegalArgumentException) {
            Log.e("MRZUtils", "Error: ${e.message}")
            return ""
        } catch (e: Exception) {
            Log.e("MRZUtils", "Unexpected error: ${e.message}")
            return ""
        }

    }

    private fun convertToMRZData(mrzRawValue: String): MRZData {
        val lines = mrzRawValue.split("\n")
        println("lines: $lines")

        if (lines.size < 2) {
            try {
                if (lines.size < 2) {
                    Log.e("MRZUtils", "Error convertToMRZData : line < 2 ")
                    throw IllegalArgumentException("Error Mrz data ")
                }
            } catch (e: Exception) {
                Log.e("MRZUtils", "Exception: ${e.message}")
                return MRZData("", "", "", "", "", "", "", "", "","")
            }
        }

        val line1 = lines[0]
        var line2 = if (lines.size > 2 && lines[1].all { it == '<' || it.isWhitespace() }) {
            lines[2]
        } else {
            lines[1]
        }

        line2 = line2.replace(" ", "").trim()
        println("line1: $line1")
        println("line2: $line2")

        val passportNumber = line2.substring(0, 9).trim().uppercase()
        val nationality = line2.substring(10, 13).trim()
//        val dateOfBirth = formatDate(line2.substring(13, 19).trim())
        val dateOfBirth = line2.substring(13, 19).trim()


        val genderIndex = line2.indexOfFirst { it == 'F' || it == 'M' }
        val gender = when {
            genderIndex != -1 -> if (line2[genderIndex] == 'M') "MALE" else "FEMALE"
            else -> ""
        }

        val expirationDate = if (genderIndex != -1 && genderIndex + 6 < line2.length) {
//            formatDate(line2.substring(genderIndex + 1, genderIndex + 7),true)  // 230117
            line2.substring(genderIndex + 1, genderIndex + 7)
        } else {
            ""
        }

        val idGard = if (line2.length >= 28) {
            val startIndex = 28
            val endIndex = line2.indexOfFirst { it == '<' || it == '«' }.takeIf { it > startIndex }
                ?: line2.length
            line2.substring(startIndex, endIndex).trim()
        } else {
            ""
        }
        val travelDoc = line1.substring(2, 5).replace('<', ' ').replace('«', ' ').trim()

        val namePart = line1.substring(5).replace('<', ' ').replace('«', ' ').trim()

        val names = namePart.split(" ")
            .map { it.uppercase() } // แปลงคำทั้งหมดเป็นตัวใหญ่
            .filter {
                it.isNotBlank() &&
                        it.all { c -> c.isLetter() } &&
                        it.length > 1 &&
                        !it.contains(Regex("(CC|KK|CK|KC|CKC|K{3,}|C{3,})"))
            }

        val lastName = names.firstOrNull() ?: ""
        val firstName = names.drop(1).joinToString(" ").trim()

        return MRZData(
            TravelDoc = travelDoc,
            DocumentNumber = passportNumber,
            IdGard = idGard,
            Surname = lastName,
            GivenNames = firstName,
            Sex = gender,
            Nationality = nationality,
            DateOfBirth = dateOfBirth,
            ExpiryDate = expirationDate,
            Image = ""
        )
    }
}