package com.gr.kakarwairider.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleMapsApiService {

    @GET("distancematrix/json")
    fun getDistanceMatrix(
        @Query("origins") origins: String,
        @Query("destinations") destinations: String,
        @Query("key") apiKey: String
    ): Call<DistanceMatrixResponse>
}