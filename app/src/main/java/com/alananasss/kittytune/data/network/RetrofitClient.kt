package com.alananasss.kittytune.data.network

import android.content.Context
import android.webkit.CookieManager
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.utils.Config
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var okHttpClient: OkHttpClient? = null

    fun resetClient() {
        okHttpClient = null
    }

    fun create(context: Context): SoundCloudApi {
        return Retrofit.Builder()
            .baseUrl(Config.BASE_URL)
            .client(getOkHttpClient(context))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SoundCloudApi::class.java)
    }

    fun getOkHttpClient(context: Context): OkHttpClient {
        if (okHttpClient == null) {
            val appContext = context.applicationContext
            val tokenManager = TokenManager(appContext)

            val cookieInterceptor = Interceptor { chain ->
                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()

                cookieHeaderFor(originalRequest.url, appContext)?.let { cookies ->
                    requestBuilder.header("Cookie", cookies)
                }

                chain.proceed(requestBuilder.build())
            }

            val authInterceptor = Interceptor { chain ->
                val originalRequest = chain.request()
                val isGraphQl = originalRequest.url.host == "graph.soundcloud.com"
                var token = if (isGraphQl) {
                    tokenManager.getAccessToken() ?: SessionManager.harvestStoredSession(appContext)
                } else {
                    SessionManager.harvestStoredSession(appContext) ?: tokenManager.getAccessToken()
                }

                if (!token.isNullOrEmpty() && !tokenManager.isGuestMode() && tokenManager.shouldRefreshAccessToken()) {
                    token = SessionManager.refreshSessionBlocking(
                        context = appContext,
                        staleToken = token,
                        timeoutMs = 6_000L
                    ) ?: token
                }

                val targetClientId = if (!token.isNullOrEmpty()) Config.OFFICIAL_CLIENT_ID else Config.CLIENT_ID

                val newUrl = originalRequest.url.let { url ->
                    if (url.host == "api-mobile.soundcloud.com") {
                        url
                    } else {
                        url.newBuilder()
                            .setQueryParameter("client_id", targetClientId)
                            .build()
                    }
                }

                val deviceId = Config.getOrCreateSoundCloudDeviceId(appContext)
                val buildVersion = "2025.12.10-release"
                val androidRelease = android.os.Build.VERSION.RELEASE ?: "10"
                val deviceModel = android.os.Build.MODEL ?: "Android"
                val customUserAgent = "SoundCloud/$buildVersion (Android $androidRelease; $deviceModel)"
                val acceptLanguage = com.alananasss.kittytune.utils.LocaleUtils.getAcceptLanguage(appContext)

                val requestBuilder = originalRequest.newBuilder()
                    .url(newUrl)
                    .header("User-Agent", customUserAgent)
                    .header("Accept", "application/json")
                    .header("Accept-Language", acceptLanguage)
                    .header("App-Version", "330120")
                    .header("UDID", deviceId)

                if (!token.isNullOrEmpty()) {
                    requestBuilder.header("Authorization", "OAuth $token")
                } else {
                    requestBuilder.removeHeader("Authorization")
                }

                chain.proceed(requestBuilder.build())
            }

            val sessionRecoveryInterceptor = Interceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)

                if (!isAuthFailure(response.code) || tokenManager.isGuestMode()) {
                    response
                } else {
                    val sentToken = request.header("Authorization")
                        ?.removePrefix("OAuth ")
                        ?.takeIf { it.isNotBlank() }
                    val currentToken = tokenManager.getAccessToken()

                    if (sentToken.isNullOrEmpty() && currentToken.isNullOrEmpty()) {
                        response
                    } else {
                        val refreshedToken = SessionManager.refreshSessionBlocking(
                            context = appContext,
                            staleToken = sentToken ?: currentToken,
                            timeoutMs = 12_000L
                        )

                        if (refreshedToken.isNullOrEmpty() || refreshedToken == sentToken) {
                            if (!sentToken.isNullOrEmpty() && canRetryWithoutAuth(request)) {
                                response.close()
                                val retryAsGuest = request.newBuilder()
                                    .removeHeader("Authorization")
                                    .build()
                                chain.proceed(retryAsGuest)
                            } else {
                                response
                            }
                        } else {
                            response.close()
                            val retryRequest = request.newBuilder()
                                .header("Authorization", "OAuth $refreshedToken")
                                .build()
                            chain.proceed(retryRequest)
                        }
                    }
                }
            }

            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(cookieInterceptor)
                .addInterceptor(authInterceptor)
                .addInterceptor(sessionRecoveryInterceptor)
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
        return okHttpClient!!
    }

    private fun isAuthFailure(code: Int): Boolean = code == 401 || code == 403

    private fun canRetryWithoutAuth(request: Request): Boolean {
        if (request.method != "GET") return false

        val path = request.url.encodedPath
        return path != "/me" &&
            !path.startsWith("/me/") &&
            !path.contains("/track_likes") &&
            !path.contains("/playlist_likes") &&
            !path.contains("/track_reposts") &&
            !path.contains("/conversations")
    }

    private fun cookieHeaderFor(url: HttpUrl, context: Context): String? {
        val cookieManager = CookieManager.getInstance()
        val candidates = listOf(
            "${url.scheme}://${url.host}",
            "https://soundcloud.com",
            "https://m.soundcloud.com"
        )

        val cookieParts = linkedSetOf<String>()
        candidates.forEach { candidate ->
            cookieManager.getCookie(candidate)
                ?.split(";")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.let(cookieParts::addAll)
        }

        val cookieDataDome = cookieParts.firstOrNull { it.startsWith("datadome=") }
        val storedDataDome = SessionManager.getStoredDataDomeCookie(context)
        val regularParts = cookieParts.filterNot { it.startsWith("datadome=") }.toMutableList()

        (cookieDataDome ?: storedDataDome)?.let { regularParts.add(it.substringBefore(";")) }

        return regularParts.joinToString("; ").takeIf { it.isNotBlank() }
    }
}

