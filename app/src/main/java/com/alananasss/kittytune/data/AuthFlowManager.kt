package com.alananasss.kittytune.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AuthFlowManager {
    private val _authCode = MutableStateFlow<String?>(null)
    val authCode: StateFlow<String?> = _authCode.asStateFlow()

    fun setAuthCode(code: String) {
        _authCode.value = code
    }

    fun clearAuthCode() {
        _authCode.value = null
    }
}
