package com.polaralias.audiofocus.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ServiceDiagnostics {
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    fun report(message: String) {
        _message.value = message
    }

    fun clear() {
        _message.value = ""
    }
}
