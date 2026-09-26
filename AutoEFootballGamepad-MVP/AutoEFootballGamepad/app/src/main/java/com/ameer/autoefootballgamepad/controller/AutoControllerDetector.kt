package com.ameer.autoefootballgamepad.controller

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import com.ameer.autoefootballgamepad.core.AppState

class AutoControllerDetector(
    context: Context,
    private val onChanged: (ControllerDescriptor?) -> Unit = { AppState.setController(it) }
) : InputManager.InputDeviceListener {

    private val inputManager = context.getSystemService(InputManager::class.java)
    private var started = false

    fun start() {
        if (started) return
        started = true
        inputManager.registerInputDeviceListener(this, null)
        refresh()
    }

    fun stop() {
        if (!started) return
        inputManager.unregisterInputDeviceListener(this)
        started = false
    }

    override fun onInputDeviceAdded(deviceId: Int) = refresh()
    override fun onInputDeviceRemoved(deviceId: Int) = refresh()
    override fun onInputDeviceChanged(deviceId: Int) = refresh()

    private fun refresh() {
        val candidates = InputDevice.getDeviceIds()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { it.isGameController() }
            .map { it.toDescriptor() }

        // Prefer a device that exposes both gamepad buttons and analog axes.
        val best = candidates.sortedWith(
            compareByDescending<ControllerDescriptor> { it.hasAnalogSticks }
                .thenByDescending { it.hasGamepadButtons }
        ).firstOrNull()

        onChanged(best)
    }

    private fun InputDevice.isGameController(): Boolean {
        val gamepad = sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        val joystick = sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        return gamepad || joystick
    }

    private fun InputDevice.toDescriptor(): ControllerDescriptor {
        val lower = name.lowercase()
        val family = when {
            vendorId == SONY_VENDOR && (
                productId == DUALSENSE_PID || productId == DUALSENSE_EDGE_PID || lower.contains("dualsense")
            ) -> ControllerFamily.DUALSENSE

            vendorId == SONY_VENDOR && (
                productId == DS4_V1_PID || productId == DS4_V2_PID ||
                    lower.contains("dualshock") || lower.contains("wireless controller")
            ) -> ControllerFamily.DUALSHOCK_4

            vendorId == MICROSOFT_VENDOR || lower.contains("xbox") || lower.contains("x-input") -> ControllerFamily.XBOX
            else -> ControllerFamily.GENERIC
        }
        return ControllerDescriptor(
            deviceId = id,
            name = name.ifBlank { "Game Controller" },
            family = family,
            vendorId = vendorId,
            productId = productId,
            hasGamepadButtons = sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD,
            hasAnalogSticks = sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        )
    }

    private companion object {
        const val SONY_VENDOR = 0x054C
        const val MICROSOFT_VENDOR = 0x045E
        const val DS4_V1_PID = 0x05C4
        const val DS4_V2_PID = 0x09CC
        const val DUALSENSE_PID = 0x0CE6
        const val DUALSENSE_EDGE_PID = 0x0DF2
    }
}
