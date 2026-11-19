package com.zionhuang.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val api_key: String,
    val userAgent: String,
    val referer: String? = null,
    val clientId: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: Int? = null,
    val isMusicClient: Boolean = false,
) {
    fun toContext(locale: YouTubeLocale, visitorData: String?) = Context(
        client = Context.Client(
            clientName = clientName,
            clientVersion = clientVersion,
            gl = locale.gl.ifBlank { "US" },
            hl = locale.hl.ifBlank { "en" },
            visitorData = visitorData,
            osName = osName,
            osVersion = osVersion,
            deviceMake = deviceMake,
            deviceModel = deviceModel,
            androidSdkVersion = androidSdkVersion,
            userAgent = userAgent.takeIf { !isMusicClient && clientName != "WEB" },
        )
    )

    companion object {
        private const val REFERER_YOUTUBE_MUSIC = "https://music.youtube.com/"

        private const val USER_AGENT_WEB =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
        private const val USER_AGENT_ANDROID =
            "com.google.android.youtube/20.10.38 (Linux; U; Android 14; en_US; Pixel 8 Pro Build/AP2A.240805.005) gzip"
        private const val USER_AGENT_IOS =
            "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
        private const val USER_AGENT_VISIONOS =
            "com.google.visionos.youtube/1.02(RealityDevice14,1; U; CPU visionOS 25_6_0 like Mac OS X)"
        private const val USER_AGENT_ANDROID_VR =
            "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

        val ANDROID_VR = YouTubeClient(
            clientName = "ANDROID_VR",
            clientId = "28",
            clientVersion = "1.65.10",
            api_key = "AIzaSyB-63vPrdThhKuerkQAhLeM_C5XCJFDU",
            userAgent = USER_AGENT_ANDROID_VR,
            osName = "Android",
            osVersion = "12L",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = 32,
        )

        val IOS = YouTubeClient(
            clientName = "IOS",
            clientId = "5",
            clientVersion = "20.10.4",
            api_key = "AIzaSyB-63vPrdThhKuerkQAhLeM_C5XCJFDU",
            userAgent = USER_AGENT_IOS,
            osName = "iPhone",
            osVersion = "18.3.2.22D82",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
        )

        val VISIONOS = YouTubeClient(
            clientName = "VISIONOS",
            clientId = "101",
            clientVersion = "1.02",
            api_key = "AIzaSyB-63vPrdThhKuerkQAhLeM_C5XCJFDU",
            userAgent = USER_AGENT_VISIONOS,
            osName = "visionOS",
            osVersion = "25.6.0",
            deviceMake = "Apple",
            deviceModel = "RealityDevice14,1",
        )

        val ANDROID_MUSIC = YouTubeClient(
            clientName = "ANDROID_MUSIC",
            clientId = "21",
            clientVersion = "7.27.52",
            api_key = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
            userAgent = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14; en_US; Pixel 8 Pro) gzip",
            osName = "Android",
            osVersion = "14",
            androidSdkVersion = 34,
            isMusicClient = true,
        )

        val ANDROID = YouTubeClient(
            clientName = "ANDROID",
            clientId = "3",
            clientVersion = "20.10.38",
            api_key = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            userAgent = USER_AGENT_ANDROID,
            osName = "Android",
            osVersion = "14",
            androidSdkVersion = 34,
        )

        val WEB = YouTubeClient(
            clientName = "WEB",
            clientId = "1",
            clientVersion = "2.20260120.01.00",
            api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3",
            userAgent = USER_AGENT_WEB,
            referer = "https://www.youtube.com/",
        )

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientId = "67",
            clientVersion = "1.20260121.03.00",
            api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
            userAgent = USER_AGENT_WEB,
            referer = REFERER_YOUTUBE_MUSIC,
            isMusicClient = true,
        )

        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientId = "85",
            clientVersion = "2.0",
            api_key = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
            userAgent = "Mozilla/5.0 (PlayStation 4 5.55) AppleWebKit/601.2 (KHTML, like Gecko)",
        )
    }
}
