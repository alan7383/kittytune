package com.alananasss.kittytune.data.zapret

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttpConfig

/**
 * Installs the in-app DPI bypass on a Ktor OkHttp engine. The socket factory gates
 * dynamically per connection, so it is set once and follows [ZapretManager] toggles
 * without rebuilding the client.
 */
fun HttpClientConfig<OkHttpConfig>.withDpiBypass() {
    engine {
        config {
            socketFactory(ZapretManager.bypassSocketFactory())
        }
    }
}
