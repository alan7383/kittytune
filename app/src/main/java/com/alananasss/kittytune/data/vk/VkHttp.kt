package com.alananasss.kittytune.data.vk

import android.content.Context
import com.alananasss.kittytune.data.network.ProxyManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * VK host list, ordered the way MeridiusCore orders them.
 *
 * Meridius talks to `vk.ru` / `api.vk.ru` / `login.vk.ru` / `id.vk.ru` exclusively — that is the
 * canonical domain today and the one that answers reliably from Russian networks and from behind a
 * VPN. KittyTune used to try `vk.com` first, which returns a *different* (often anti-bot gated)
 * response and a session bound to another cookie domain, so `vk.ru` is now the primary and `vk.com`
 * is only a fallback.
 */
object VkEndpoints {

    val WEB_HOSTS = listOf("https://vk.ru", "https://vk.com")
    val API_HOSTS = listOf("https://api.vk.ru", "https://api.vk.com")
    val LOGIN_HOSTS = listOf("https://login.vk.ru", "https://login.vk.com")
    val ID_HOSTS = listOf("https://id.vk.ru", "https://id.vk.com")

    /** API version MeridiusCore 2.5.0 pins for the auth + users endpoints. */
    const val API_VERSION = "5.258"

    /** App id Meridius passes to the VK ID auth page (`configuration.auth.app.id`). */
    const val AUTH_APP_ID = 7913379

    /** VK Windows client id, used for `act=web_token` and the official audio API. */
    const val WEB_TOKEN_APP_ID = 6287487

    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0"

    const val ANDROID_USER_AGENT =
        "VKAndroidApp/9.2.0-24200 (Android 11; SDK 30; arm64-v8a; Xiaomi M2003J15SC; ru; 2340x1080)"

    fun alAudio(host: String) = "$host/al_audio.php"
    fun alArtist(host: String) = "$host/al_artist.php"
    fun page(host: String, path: String) = host + "/" + path.trimStart('/')

    fun isRu(host: String) = host.endsWith(".ru")
    fun origin(host: String) = host
    fun referer(host: String) = "$host/"
}

/**
 * One OkHttp client, one cookie jar, shared by every VK component.
 *
 * Previously `VkApi`, `VkQrAuthManager` and the profile screens each built their own client with
 * their own (or no) cookie storage, so the session established by the QR login was invisible to the
 * search code. Everything now funnels through here.
 */
object VkHttp {

    @Volatile
    private var jar: VkCookieJar? = null

    @Volatile
    private var base: OkHttpClient? = null

    fun cookieJar(context: Context): VkCookieJar =
        jar ?: synchronized(this) {
            jar ?: VkCookieJar(context.applicationContext).also { jar = it }
        }

    /**
     * Proxy settings can change at runtime (users toggle their VPN/proxy constantly), so the proxy
     * is applied to a fresh builder on every call while the connection pool and the cookie jar stay
     * shared.
     */
    fun client(context: Context): OkHttpClient {
        val shared = base ?: synchronized(this) {
            base ?: OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .cookieJar(cookieJar(context))
                .build()
                .also { base = it }
        }
        return ProxyManager.configureOkHttpClient(shared.newBuilder()).build()
    }
}
