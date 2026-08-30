package com.alananasss.kittytune.data.vk

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class VkQrCodeData(
    val authUrl: String,
    val authHash: String,
    val authId: String,
    val authCode: String,
    val anonymousToken: String,
    val expiresIn: Long,
    val uuid: String
)

sealed class VkQrPollState {
    object WaitingScan : VkQrPollState()
    object ScannedWaitingConfirm : VkQrPollState()
    data class Success(
        val token: String?,
        val userId: Long,
        val remixsid: String? = null,
        val remixnsid: String? = null,
        val remixdsid: String? = null,
        val firstName: String = "",
        val lastName: String = "",
        val photoUrl: String? = null,
        val screenName: String? = null
    ) : VkQrPollState()
    object Expired : VkQrPollState()
    data class Error(val message: String) : VkQrPollState()
}

class VkQrAuthManager(private val context: Context) {

    private val client: OkHttpClient
        get() = VkHttp.client(context)

    private val cookieJar: VkCookieJar
        get() = VkHttp.cookieJar(context)

    /** `window.init.auth` from the VK ID page. */
    private data class AuthInit(
        val anonymousToken: String,
        val hostAppId: Int
    )

    private var authInit: AuthInit? = null

    suspend fun requestQrCode(): VkQrCodeData? = withContext(Dispatchers.IO) {
        val uuid = generateRandomUuid()
        val actionPayload = JSONObject().apply {
            put("name", "qr_auth")
            put("token", "qr_auth_scanned")
            put("entry", JSONObject().apply {
                put("source", "main")
                put("screen", "start")
            })
        }.toString()

        val actionB64 = Base64.encodeToString(actionPayload.toByteArray(), Base64.NO_WRAP)
        val encodedAction = java.net.URLEncoder.encode(actionB64, "UTF-8")

        val authPageUrls = listOf(
            "https://id.vk.ru/auth?action=$encodedAction&scheme=dark&is_redesigned=1&response_type=silent_token&v=1.3.0&redirect_uri=https%3A%2F%2Fvk.ru%2F&uuid=$uuid&app_id=$VK_APP_ID",
            "https://id.vk.com/auth?action=$encodedAction&scheme=dark&is_redesigned=1&response_type=silent_token&v=1.3.0&redirect_uri=https%3A%2F%2Fvk.com%2F&uuid=$uuid&app_id=$VK_APP_ID"
        )

        var init: AuthInit? = null
        for (authPageUrl in authPageUrls) {
            try {
                val pageReq = Request.Builder()
                    .url(authPageUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                val html = client.newCall(pageReq).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body.string()
                }

                if (!html.isNullOrBlank()) {
                    init = extractAuthInit(html)
                    if (init != null) break
                }
            } catch (e: Exception) {
                Log.d(TAG, "Auth page fetch failed on $authPageUrl: ${e.message}")
            }
        }

        if (init == null) return@withContext null
        authInit = init
        val anonymousToken = init.anonymousToken

        val codeReqBody = FormBody.Builder()
            // Meridius announces itself as the VK ID desktop web client.
            .add("device_name", "Windows NT 10.0; Win64; x64")
            .add("auth_code_flow", "0")
            .add("verification_hash", "")
            .add("force_regenerate", "0")
            .add("anonymous_token", anonymousToken)
            .add("is_switcher_flow", "")
            .add("access_token", "")
            .build()

        // `client_id` must be the host app id advertised by the auth page, not our own app id:
        // VK rejects the auth code otherwise, which broke the QR flow intermittently.
        val getCodeUrls = VkEndpoints.API_HOSTS.map {
            "$it/method/auth.getAuthCode?v=${VkEndpoints.API_VERSION}&client_id=${init.hostAppId}"
        }

        for (codeUrl in getCodeUrls) {
            try {
                val codeReq = Request.Builder()
                    .url(codeUrl)
                    .post(codeReqBody)
                    .header("User-Agent", USER_AGENT)
                    .header("Origin", "https://id.vk.ru")
                    .header("Referer", "https://id.vk.ru/")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()

                val res = client.newCall(codeReq).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body.string()
                }

                if (!res.isNullOrBlank()) {
                    val json = JSONObject(res)
                    val responseObj = json.optJSONObject("response")
                    if (responseObj != null) {
                        val authCode = responseObj.optString("auth_code", "")
                        val authHash = responseObj.optString("auth_hash", "")
                        val authId = responseObj.optString("auth_id", "")
                        val authUrl = responseObj.optString("auth_url", "")
                        val expiresIn = responseObj.optLong("expires_in", 0L)

                        if (authUrl.isNotBlank() && authHash.isNotBlank()) {
                            return@withContext VkQrCodeData(
                                authUrl = authUrl,
                                authHash = authHash,
                                authId = authId,
                                authCode = authCode,
                                anonymousToken = anonymousToken,
                                expiresIn = expiresIn,
                                uuid = uuid
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "getAuthCode failed on $codeUrl: ${e.message}")
            }
        }

        null
    }

    suspend fun checkQrStatus(qrData: VkQrCodeData): VkQrPollState = withContext(Dispatchers.IO) {
        val checkReqBody = FormBody.Builder()
            .add("auth_hash", qrData.authHash)
            .add("web_auth", "1")
            .add("anonymous_token", qrData.anonymousToken)
            .add("access_token", "")
            .build()

        val checkUrls = VkEndpoints.API_HOSTS.map {
            "$it/method/auth.checkAuthCode?v=${VkEndpoints.API_VERSION}&client_id=${hostAppId()}"
        }

        var lastError: String? = null

        for (checkUrl in checkUrls) {
            try {
                val checkReq = Request.Builder()
                    .url(checkUrl)
                    .post(checkReqBody)
                    .header("User-Agent", USER_AGENT)
                    .header("Origin", "https://id.vk.ru")
                    .header("Referer", "https://id.vk.ru/")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()

                client.newCall(checkReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        lastError = "HTTP ${resp.code}"
                        return@use
                    }
                    val body = resp.body.string()
                    val json = JSONObject(body)

                    if (json.has("error")) {
                        val err = json.optJSONObject("error")
                        val errCode = err?.optString("error_code") ?: ""
                        val errMsg = err?.optString("error_msg") ?: "Unknown error"
                        if (errCode == "qr_expired" || errMsg.contains("expire", ignoreCase = true)) {
                            return@withContext VkQrPollState.Expired
                        }
                        return@withContext VkQrPollState.Error(errMsg)
                    }

                    val responseObj = json.optJSONObject("response")
                        ?: return@use

                    val status = responseObj.optInt("status", 0)
                    when (status) {
                        0, 1 -> return@withContext VkQrPollState.WaitingScan
                        3, 5 -> return@withContext VkQrPollState.ScannedWaitingConfirm
                        2 -> {
                            val token = responseObj.optString("super_app_token")
                                .takeIf { it.isNotBlank() }
                                ?: responseObj.optString("access_token")
                                    .takeIf { it.isNotBlank() }
                                ?: responseObj.optString("token")
                                    .takeIf { it.isNotBlank() }
                                ?: responseObj.optString("auth_code")
                                    .takeIf { it.isNotBlank() }

                            var userId = responseObj.optLong("user_id", 0L)
                            if (userId == 0L && !token.isNullOrBlank()) {
                                userId = extractUserIdFromJwt(token)
                            }

                            // Complete full exchange via connect_code_auth + web_token + users.get
                            val fullAuth = completeFullAuth(token ?: "", qrData.uuid, userId)

                            return@withContext VkQrPollState.Success(
                                token = fullAuth.accessToken ?: token,
                                userId = if (fullAuth.userId > 0L) fullAuth.userId else userId,
                                remixsid = fullAuth.remixsid,
                                remixnsid = fullAuth.remixnsid,
                                remixdsid = fullAuth.remixdsid,
                                firstName = fullAuth.firstName,
                                lastName = fullAuth.lastName,
                                photoUrl = fullAuth.photoUrl,
                                screenName = fullAuth.screenName
                            )
                        }
                        else -> {
                            if (status < 0) return@withContext VkQrPollState.Expired
                            else return@withContext VkQrPollState.WaitingScan
                        }
                    }
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Network error"
                Log.d(TAG, "checkAuthCode failed on $checkUrl: ${e.message}")
            }
        }

        VkQrPollState.Error(lastError ?: "Network error")
    }

    suspend fun validateAuthCode(qrData: VkQrCodeData, code: String): VkQrPollState = withContext(Dispatchers.IO) {
        val validateBody = FormBody.Builder()
            .add("auth_hash", qrData.authHash)
            .add("validation_code", code.trim())
            .add("access_token", qrData.anonymousToken)
            .build()

        val urls = VkEndpoints.API_HOSTS.map {
            "$it/method/auth.validateAuthCode?v=${VkEndpoints.API_VERSION}&client_id=${hostAppId()}"
        }

        for (url in urls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .post(validateBody)
                    .header("User-Agent", USER_AGENT)
                    .header("Origin", "https://id.vk.ru")
                    .header("Referer", "https://id.vk.ru/")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body.string()
                        val json = JSONObject(body)
                        if (!json.has("error")) {
                            return@withContext checkQrStatus(qrData)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "validateAuthCode failed on $url: ${e.message}")
            }
        }

        VkQrPollState.Error("Invalid confirmation code")
    }

    private data class FullAuthSession(
        val accessToken: String?,
        val userId: Long,
        val remixsid: String?,
        val remixnsid: String?,
        val remixdsid: String?,
        val firstName: String = "",
        val lastName: String = "",
        val photoUrl: String? = null,
        val screenName: String? = null
    )

    private suspend fun completeFullAuth(
        token: String,
        uuid: String,
        initialUserId: Long
    ): FullAuthSession = withContext(Dispatchers.IO) {
        // 1. Exchange the silent token for a real web session. Everything VK sets along the way is
        //    captured by the shared cookie jar, so no cookie has to be copied by hand.
        val toB64 = Base64.encodeToString("https://vk.ru/".toByteArray(), Base64.NO_WRAP)
        val body = FormBody.Builder()
            .add("token", token)
            .add("uuid", uuid)
            .add("app_id", hostAppId().toString())
            .add("flow_start_state", "")
            .add("is_external_carousel", "")
            .add("oauth_version", "")
            .add("sid", "")
            .add("oauth_force_hash", "0")
            .add("is_registration", "0")
            .add("oauth_response_type", "silent_token")
            .add("vkid_oauth_hash", "")
            .add("is_oauth_migrated_flow", "0")
            .add("oauth_state", "")
            .add("to", toB64)
            .add("version", "1")
            .build()

        for (host in VkEndpoints.LOGIN_HOSTS) {
            try {
                val request = Request.Builder()
                    .url("$host/?act=connect_code_auth")
                    .post(body)
                    .header("User-Agent", USER_AGENT)
                    .header("Origin", VkEndpoints.ID_HOSTS.first())
                    .header("Referer", VkEndpoints.ID_HOSTS.first() + "/")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()

                val nextStepUrl = client.newCall(request).execute().use { response ->
                    val raw = response.body.string()
                    runCatching {
                        JSONObject(raw).optJSONObject("data")?.optString("next_step_url")
                    }.getOrNull()
                }

                // `final()` in Meridius: following this redirect is what actually mints remixsid.
                if (!nextStepUrl.isNullOrBlank()) {
                    val nextRequest = Request.Builder()
                        .url(nextStepUrl)
                        .header("User-Agent", USER_AGENT)
                        .build()
                    client.newCall(nextRequest).execute().use { it.body.string() }
                }
            } catch (e: Exception) {
                Log.d(TAG, "connect_code_auth on $host failed: ${e.message}")
            }

            if (cookieJar.hasSession()) break
        }

        // Land on the feed once, the way Meridius does, so VK finishes setting up the session.
        if (cookieJar.hasSession()) {
            try {
                val warmup = Request.Builder()
                    .url(VkEndpoints.WEB_HOSTS.first() + "/feed")
                    .header("User-Agent", USER_AGENT)
                    .build()
                client.newCall(warmup).execute().use { it.body.string() }
            } catch (e: Exception) {
                Log.d(TAG, "Session warm-up failed: ${e.message}")
            }
        }

        val tokenManager = VkTokenManager(context)
        var finalUserId = initialUserId
        if (finalUserId == 0L) {
            finalUserId = cookieJar.value("remixmid")?.toLongOrNull()
                ?: cookieJar.value("l")?.toLongOrNull()
                ?: 0L
        }
        if (finalUserId > 0L) tokenManager.userId = finalUserId

        // 2. Mint the official API token and 3. read the profile — both live in VkApi already.
        val api = VkApi(context)
        val webToken = api.obtainWebToken()
        if (finalUserId == 0L) finalUserId = tokenManager.userId

        val profile = if (finalUserId > 0L) api.fetchUserProfile(finalUserId) else null

        FullAuthSession(
            accessToken = webToken ?: token.takeIf { it.startsWith("vk1.a") } ?: token,
            userId = if (finalUserId > 0L) finalUserId else initialUserId,
            remixsid = cookieJar.value("remixsid"),
            remixnsid = cookieJar.value("remixnsid"),
            remixdsid = cookieJar.value("remixdsid"),
            firstName = profile?.firstName.orEmpty(),
            lastName = profile?.lastName.orEmpty(),
            photoUrl = profile?.photoMax,
            screenName = profile?.screenName
        )
    }
    private fun generateRandomUuid(): String {
        val allowedChars = ('a'..'z') + ('0'..'9')
        return (1..6)
            .map { allowedChars.random() }
            .joinToString("")
    }

    private fun hostAppId(): Int = authInit?.hostAppId ?: VkEndpoints.AUTH_APP_ID

    /** Reads `window.init` off the VK ID page — port of Meridius' `LOGIN_INIT` regex. */
    private fun extractAuthInit(html: String): AuthInit? {
        try {
            val initIndex = html.indexOf("window.init = ")
            if (initIndex == -1) return null
            val after = html.substring(initIndex + "window.init = ".length)
            var depth = 0
            var endIdx = 0
            for (i in after.indices) {
                if (after[i] == '{') depth++
                else if (after[i] == '}') {
                    depth--
                    if (depth == 0) {
                        endIdx = i + 1
                        break
                    }
                }
            }
            if (endIdx <= 0) return null
            val authObj = JSONObject(after.substring(0, endIdx)).optJSONObject("auth") ?: return null
            val token = authObj.optString("anonymous_token")
            if (token.isBlank()) return null
            return AuthInit(
                anonymousToken = token,
                hostAppId = authObj.optInt("host_app_id", VkEndpoints.AUTH_APP_ID)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing window.init: ${e.message}", e)
            return null
        }
    }

    private fun extractUserIdFromJwt(token: String): Long {
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payloadB64 = parts[1]
                val decoded = Base64.decode(payloadB64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                val json = JSONObject(String(decoded, Charsets.UTF_8))
                json.optLong("user_id", 0L).takeIf { it != 0L }
                    ?: json.optLong("sub", 0L).takeIf { it != 0L }
                    ?: json.optLong("id", 0L)
            } else 0L
        } catch (e: Exception) {
            0L
        }
    }

    companion object {
        private const val TAG = "VkQrAuthManager"
        private const val VK_APP_ID = VkEndpoints.AUTH_APP_ID
        private const val USER_AGENT = VkEndpoints.BROWSER_USER_AGENT
    }
}
