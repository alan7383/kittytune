    package com.alananasss.kittytune.data.network
    
    import android.content.Context
    import android.webkit.CookieManager
    import com.alananasss.kittytune.data.TokenManager
    import com.alananasss.kittytune.utils.Config
    import okhttp3.Interceptor
    import okhttp3.OkHttpClient
    import okhttp3.logging.HttpLoggingInterceptor
    import retrofit2.Retrofit
    import retrofit2.converter.gson.GsonConverterFactory
    import java.util.concurrent.TimeUnit
    
    object RetrofitClient {
        private var okHttpClient: OkHttpClient? = null
    
        fun create(context: Context): SoundCloudApi {
            val tokenManager = TokenManager(context)
    
            val cookieInterceptor = Interceptor { chain ->
                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
    
                val cookieManager = CookieManager.getInstance()
                val cookies = cookieManager.getCookie("https://soundcloud.com")
    
                if (!cookies.isNullOrEmpty()) {
                    requestBuilder.header("Cookie", cookies)
                }
    
                chain.proceed(requestBuilder.build())
            }
    
            val authInterceptor = Interceptor { chain ->
                val originalRequest = chain.request()
                val token = tokenManager.getAccessToken()
    
                // Using Config.CLIENT_ID
                val newUrl = originalRequest.url.newBuilder()
                    .addQueryParameter("client_id", Config.CLIENT_ID)
                    .build()
    
                val requestBuilder = originalRequest.newBuilder()
                    .url(newUrl)
                    .header("User-Agent", Config.USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Origin", "https://soundcloud.com")
                    .header("Referer", "https://soundcloud.com/")
    
                if (!token.isNullOrEmpty() && token != "null") {
                    requestBuilder.header("Authorization", "OAuth $token")
                }
    
                chain.proceed(requestBuilder.build())
            }
    
            if (okHttpClient == null) {
                okHttpClient = OkHttpClient.Builder()
                    .addInterceptor(cookieInterceptor)
                    .addInterceptor(authInterceptor)
                    .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()
            }
    
            return Retrofit.Builder()
                .baseUrl(Config.BASE_URL)
                .client(okHttpClient!!)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SoundCloudApi::class.java)
        }
    }


