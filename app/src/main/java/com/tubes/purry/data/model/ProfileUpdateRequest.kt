package com.tubes.purry.data.model

import com.google.gson.annotations.SerializedName

data class ProfileUpdateRequest(
    @SerializedName("location") val location: String
)