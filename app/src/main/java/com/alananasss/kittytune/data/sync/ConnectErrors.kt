package com.alananasss.kittytune.data.sync

/**
 * Why a Connect link ended, as something a screen can explain. A bare "SocketTimeoutException" told the
 * listener nothing; a timeout on the LAN almost always means a firewall or a different network, which
 * the screen can say in words.
 */
object ConnectErrors {
    enum class Kind { TIMEOUT, REFUSED, DNS, UNAUTHORIZED, HTTP, OTHER }

    data class Parsed(val transport: String, val kind: Kind, val raw: String)

    /** Encodes [raw] (what the socket listener reported) with the route it happened on. */
    fun code(transport: String, raw: String): String = "$transport|${kindOf(raw).name}|$raw"

    fun parse(code: String): Parsed {
        val parts = code.split('|', limit = 3)
        if (parts.size < 3) return Parsed("", kindOf(code), code)
        val kind = runCatching { Kind.valueOf(parts[1]) }.getOrDefault(Kind.OTHER)
        return Parsed(parts[0], kind, parts[2])
    }

    fun kindOf(raw: String): Kind = when {
        raw.contains("Timeout", ignoreCase = true) -> Kind.TIMEOUT
        raw.contains("UnknownHost") -> Kind.DNS
        raw.startsWith("WebSocket 1008") || raw == "HTTP 401" || raw == "HTTP 403" -> Kind.UNAUTHORIZED
        raw.startsWith("HTTP ") -> Kind.HTTP
        raw.contains("ConnectException") || raw.contains("NoRouteToHost") ||
            raw.contains("PortUnreachable") || raw.contains("EOFException") -> Kind.REFUSED
        else -> Kind.OTHER
    }
}
