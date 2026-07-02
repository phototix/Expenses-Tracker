package com.brandon.expensestracker.data.remote

import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.QueryMap

interface MasterAuthApi {
    @POST("auth/register")
    suspend fun register(@Body payload: JsonObject): JsonObject

    @POST("auth/login")
    suspend fun login(@Body payload: JsonObject): JsonObject

    @GET("config/app")
    suspend fun getAppConfig(@QueryMap payload: Map<String, String>): JsonObject

    @POST("config/app")
    suspend fun updateAppConfig(@Body payload: JsonObject): JsonObject
}
