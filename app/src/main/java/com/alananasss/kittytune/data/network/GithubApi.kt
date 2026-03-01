package com.alananasss.kittytune.data.network

import com.alananasss.kittytune.BuildConfig
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class GithubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val body: String,
    @SerializedName("assets") val assets: List<GithubAsset>
)

data class GithubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("url") val apiUrl: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("content_type") val contentType: String,
    @SerializedName("size") val size: Long
)

interface GithubApiService {
    @GET("repos/alan7383/kittytune/releases/latest")
    suspend fun getLatestRelease(): GithubRelease
}

object GithubClient {
    private const val BASE_URL = "https://api.github.com/"

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Accept", "application/vnd.github.v3+json")
            .build()
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .build()

    val api: GithubApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GithubApiService::class.java)
    }
}