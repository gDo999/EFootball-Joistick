package com.ameer.autoefootballgamepad.core

import com.ameer.autoefootballgamepad.controller.ControllerDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppUiState(
    val controller: ControllerDescriptor? = null,
    val efootballDetected: Boolean = false,
    val mappingActive: Boolean = false,
    val calibrationConfidence: Float? = null,
    val controlLayout: String = "Unknown",
    val inputMode: String = "Waiting",
    val lastError: String? = null,
    val fullAnalogSupport: Boolean = android.os.Build.VERSION.SDK_INT >= 34
)

object AppState {
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    fun setController(controller: ControllerDescriptor?) {
        _state.update { it.copy(controller = controller) }
    }

    fun setEfootballDetected(detected: Boolean) {
        _state.update { it.copy(efootballDetected = detected) }
    }

    fun setMappingActive(active: Boolean, mode: String = if (active) "Touch mapping" else "Waiting") {
        _state.update { it.copy(mappingActive = active, inputMode = mode) }
    }

    fun setCalibration(confidence: Float?, layout: String) {
        _state.update { it.copy(calibrationConfidence = confidence, controlLayout = layout) }
    }

    fun setError(message: String?) {
        _state.update { it.copy(lastError = message) }
    }
}
