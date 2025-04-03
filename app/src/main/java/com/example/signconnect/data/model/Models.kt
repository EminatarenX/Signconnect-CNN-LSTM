package com.example.signconnect.data.model

import com.google.gson.annotations.SerializedName

data class ApiResponse(
    @SerializedName("predictions")
    val predictions: List<Prediction>
)

data class Prediction(
    @SerializedName("rank")
    val rank: Int,
    @SerializedName("class")
    val className: String,
    @SerializedName("probability")
    val probability: Float
)
