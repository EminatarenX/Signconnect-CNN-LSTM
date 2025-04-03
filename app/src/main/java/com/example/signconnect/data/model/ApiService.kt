package com.example.signconnect.data.model


import com.example.signconnect.data.model.ApiResponse
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

interface ApiService {
    @Multipart
    @POST("predict")
    suspend fun predictSignLanguage(
        @Part video: MultipartBody.Part
    ): Response<ApiResponse>

    companion object {
        // Cambia esta URL a la de tu API Flask en producción
        // Para emulador local usa http://10.0.2.2:5000/
        private const val BASE_URL = "https://j1hw3m3x-4001.use2.devtunnels.ms/"

        fun create(): ApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(ApiService::class.java)
        }
    }
}