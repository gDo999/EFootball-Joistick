package com.ameer.autoefootballgamepad.controller

enum class ControllerFamily {
    DUALSHOCK_4,
    DUALSENSE,
    XBOX,
    GENERIC
}

data class ControllerDescriptor(
    val deviceId: Int,
    val name: String,
    val family: ControllerFamily,
    val vendorId: Int,
    val productId: Int,
    val hasGamepadButtons: Boolean,
    val hasAnalogSticks: Boolean
)
