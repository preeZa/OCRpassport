package com.example.ocrpassport.component.models

import android.graphics.Bitmap
import com.example.ocrpassport.component.util.ImageUtil
import org.jmrtd.lds.iso19794.FaceImageInfo
import java.io.Serializable

data class PersonDetails(
    var name: String? = null,
    var surname: String? = null,
    var personalNumber: String? = null,
    var gender: String? = null,
    var birthDate: String? = null,
    var expiryDate: String? = null,
    var serialNumber: String? = null,
    var nationality: String? = null,
    var issuerAuthority: String? = null,
    var faceImageInfo: FaceImageInfo? = null,
    var portraitImageBase64: String? = null,
    var signature: Bitmap? = null,
    var signatureBase64: String? = null,
    var fingerprints: List<Bitmap>? = null
) : Serializable {
    override fun toString(): String {
        return  "    TravelDoc = $issuerAuthority\n" +
                "    DocumentNumber = $serialNumber\n" +
                "    IdGard = $personalNumber\n" +
                "    GivenNames = $name\n" +
                "    Surname = $surname\n" +
                "    Sex = $gender\n" +
                "    Nationality = $nationality\n" +
                "    DateOfBirth = $birthDate\n" +
                "    ExpiryDate = $expiryDate\n"


    }
    fun getPortraitImage(): Bitmap? {
        if (this.faceImageInfo == null) {
            if (this.portraitImageBase64 == null) {
                return null
            }

            return ImageUtil.getImageFromBase64(this.portraitImageBase64!!)
        }

        return ImageUtil.getImage(this.faceImageInfo!!).bitmapImage
    }
}