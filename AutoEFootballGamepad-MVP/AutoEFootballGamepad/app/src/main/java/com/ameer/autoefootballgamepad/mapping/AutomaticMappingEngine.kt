package com.ameer.autoefootballgamepad.mapping

import com.ameer.autoefootballgamepad.core.AppState

class AutomaticMappingEngine(
    private val gestureEngine: MultiTouchGestureEngine,
    private val resolutionAdapter: ResolutionAdapter
) {
    private var normalizedProfile: MappingProfile = EfootballPresets.classicLandscape
    private var active = false

    fun setProfile(profile: MappingProfile) {
        normalizedProfile = profile
        gestureEngine.updateProfile(resolutionAdapter.toPixels(profile))
    }

    fun currentProfile(): MappingProfile = normalizedProfile

    fun isActive(): Boolean = active

    fun setActive(enabled: Boolean) {
        active = enabled
        gestureEngine.setEnabled(enabled)
        AppState.setMappingActive(enabled, if (enabled) "Touch mapper · 1:1 controller input" else "Waiting")
    }

    fun move(x: Float, y: Float) {
        if (active) gestureEngine.setStick(x, y)
    }

    fun button(target: TouchTarget, down: Boolean) {
        if (active) gestureEngine.setButton(target, down)
    }

    fun stop() {
        active = false
        gestureEngine.setEnabled(false)
        AppState.setMappingActive(false, "Waiting")
    }
}
