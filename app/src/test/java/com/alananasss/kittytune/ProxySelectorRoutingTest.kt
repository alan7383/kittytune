package com.alananasss.kittytune

import com.alananasss.kittytune.data.network.ProxyManager
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the proxy-disable path: routing must stay dynamic through the app [java.net.ProxySelector]
 * instead of being baked into each client with `builder.proxy(...)`.
 *
 * A baked proxy is a static snapshot — every client built while the proxy was enabled (Retrofit's
 * cached client, SocialProofRepository's cached API, ViewModels' `api` fields) keeps dialing the
 * dead proxy after it is disabled (`ECONNREFUSED` on 127.0.0.1:1080) until the process dies.
 */
class ProxySelectorRoutingTest {

    @Test
    fun clientNeverBakesStaticProxy() {
        val client = ProxyManager.configureOkHttpClient(OkHttpClient.Builder()).build()
        assertNull("proxy must not be baked into the client", client.proxy)
        assertNotNull("dynamic selector must be installed", client.proxySelector)
    }
}
