    package com.alananasss.kittytune.data.network
    
    import com.google.gson.annotations.SerializedName
    import retrofit2.Retrofit
    import retrofit2.converter.gson.GsonConverterFactory
    import retrofit2.http.GET
    
    data class GithubRelease(
        @SerializedName("tag_name") val tagName: String,
        @SerializedName("body") val body: String, // Changelog
        @SerializedName("assets") val assets: List<GithubAsset>
    )
    
    data class GithubAsset(
        @SerializedName("browser_download_url") val downloadUrl: String,
        @SerializedName("content_type") val contentType: String,
        @SerializedName("size") val size: Long
    )
    
    interface GithubApiService {
        @GET("repos/alan7383/kittytune/releases/latest")
        suspend fun getLatestRelease(): GithubRelease
    }
    
    object GithubClient {
        private const val BASE_URL = "https://api.github.com/"
    
        val api: GithubApiService by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GithubApiService::class.java)
        }
    }


