package com.vemy.rollcall

import retrofit2.Call
import retrofit2.http.GET

data class RollcallResponse(val content: String)

interface RollcallApiService {
    @GET("api/radar/rollcalls")
    fun getRollcallData(): Call<RollcallResponse>
}