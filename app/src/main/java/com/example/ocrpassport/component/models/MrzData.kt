package com.example.ocrpassport.component.models

import java.io.Serializable


data class MRZData(
    val TravelDoc: String?,
    val DocumentNumber: String?,
    val IdGard: String?,
    val Surname: String?,
    val GivenNames: String?,
    val Sex: String?,
    val Nationality: String?,
    val DateOfBirth: String?,
    val ExpiryDate: String?,
    val Image: String?,
) : Serializable
{

    override fun toString(): String {
        return  "    TravelDoc = $TravelDoc\n" +
                "    DocumentNumber = $DocumentNumber\n" +
                "    IdGard = $IdGard\n" +
                "    Surname = $Surname\n" +
                "    GivenNames = $GivenNames\n" +
                "    Sex = $Sex\n" +
                "    Nationality = $Nationality\n" +
                "    DateOfBirth = $DateOfBirth\n" +
                "    ExpiryDate = $ExpiryDate\n" +
                "    Image = $Image\n"

    }
}

