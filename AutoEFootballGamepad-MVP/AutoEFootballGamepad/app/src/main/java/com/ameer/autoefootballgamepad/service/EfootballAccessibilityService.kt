package com.ameer.autoefootballgamepad.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import com.ameer.autoefootballgamepad.calibration.AutoCalibrationManager
import com.ameer.autoefootballgamepad.controller.AutoControllerDetector
import com.ameer.autoefootballgamepad.controller.ControllerDescriptor
import com.ameer.autoefootballgamepad.controller.ControllerFamily
import com.ameer.autoefootballgamepad.controller.GamepadInputManager
import com.ameer.autoefootballgamepad.core.AppState
import com.ameer.autoefootballgamepad.detection.EfootballScreenDetector
import com.ameer.autoefootballgamepad.mapping.AutomaticMappingEngine
import com.ameer.autoefootballgamepad.mapping.EfootballPresets
import com.ameer.autoefootballgamepad.mapping.MappingProfile
import com.ameer.autoefootballgamepad.mapping.MultiTouchGestureEngine
import com.ameer.autoefootballgamepad.mapping.ResolutionAdapter
import com.ameer.autoefootballgamepad.util.EfootballLauncher
import com.ameer.autoefootballgamepad.util.ErrorLogger

class EfootballAccessibilityService : AccessibilityService() {

    private lateinit var detector: AutoControllerDetector
    private lateinit var mappingEngine: AutomaticMappingEngine
    private lateinit var inputManager: GamepadInputManager
    private lateinit var calibrationManager: AutoCalibrationManager
    private lateinit var logger: ErrorLogger
    private val handler = Handler(Looper.getMainLooper())

    private var efootballForeground = false
    private var touchMapperCaptureEnabled = false
    private var calibrationScheduled = false
    private var calibrationAttempts = 0
    private var hudMisses = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        logger = ErrorLogger(this)

        val resolutionAdapter = ResolutionAdapter(this)
        val gestureEngine = MultiTouchGestureEngine(this)
        mappingEngine = AutomaticMappingEngine(gestureEngine, resolutionAdapter)
        inputManager = GamepadInputManager(mappingEngine)
        calibrationManager = AutoCalibrationManager(
            service = this,
            detector = EfootballScreenDetector(),
            resolutionAdapter = resolutionAdapter,
            logger = logger
        )

        // Start in pass-through mode. On Android 14+, any detected controller switches to
        // deterministic 1:1 touch translation so Bluetooth and USB use the same path.
        configureInputCapture(null)

        detector = AutoControllerDetector(this) { descriptor ->
            AppState.setController(descriptor)
            // Input capture is enabled only after a usable match mapping is ready.
            if (efootballForeground) updateModeForController(descriptor)
        }
        detector.start()

        if (Build.VERSION.SDK_INT < 34) {
            logger.log(
                "Android 14+ is required for the full controller-to-touch path. " +
                    "On older Android versions, known supported controllers fall back to eFootball's native input."
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == EfootballLauncher.PACKAGE_NAME) {
            if (!efootballForeground) {
                efootballForeground = true
                AppState.setEfootballDetected(true)
                calibrationAttempts = 0
                hudMisses = 0
                val controller = AppState.state.value.controller
                updateModeForController(controller)
            }
            return
        }

        // Only treat a real window-state change to another app as leaving the game.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && efootballForeground) {
            if (pkg != packageName && !pkg.startsWith("com.android.systemui")) {
                efootballForeground = false
                AppState.setEfootballDetected(false)
                configureInputCapture(null)
                mappingEngine.stop()
                calibrationScheduled = false
                calibrationAttempts = 0
                hudMisses = 0
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!touchMapperCaptureEnabled || !efootballForeground || !::inputManager.isInitialized) return false
        return inputManager.onKeyEvent(event)
    }

    override fun onMotionEvent(event: MotionEvent) {
        if (Build.VERSION.SDK_INT < 34 || !touchMapperCaptureEnabled || !efootballForeground || !::inputManager.isInitialized) return
        inputManager.onMotionEvent(event)
    }

    override fun onInterrupt() {
        if (::mappingEngine.isInitialized) mappingEngine.stop()
    }

    override fun onDestroy() {
        if (::detector.isInitialized) detector.stop()
        if (::mappingEngine.isInitialized) mappingEngine.stop()
        if (::calibrationManager.isInitialized) calibrationManager.shutdown()
        runCatching { configureInputCapture(null) }
        AppState.setEfootballDetected(false)
        super.onDestroy()
    }

    private fun updateModeForController(controller: ControllerDescriptor?) {
        when {
            controller == null -> {
                configureInputCapture(null)
                mappingEngine.stop()
                hudMisses = 0
                AppState.setMappingActive(false, "Waiting for controller")
            }

            Build.VERSION.SDK_INT >= 34 -> {
                // Full MVP path: translate controller input 1:1 into touch. On first use, wait
                // for the Classic match HUD before consuming controller events.
                prepareTouchMapping()
            }

            else -> {
                configureInputCapture(null)
                // Android 11-13 cannot globally capture joystick MotionEvents through Accessibility.
                // Pass the controller through untouched. KONAMI documents native support for
                // DualSense/DUALSHOCK 4/Xbox; other controllers may work but are not guaranteed.
                mappingEngine.stop()
                val mode = if (controller.family == ControllerFamily.GENERIC) {
                    "Native pass-through · Generic compatibility not guaranteed"
                } else {
                    "Native eFootball fallback (Android <14)"
                }
                AppState.setMappingActive(true, mode)
            }
        }
    }

    private fun configureInputCapture(controller: ControllerDescriptor?) {
        val shouldCapture = controller != null && Build.VERSION.SDK_INT >= 34
        touchMapperCaptureEnabled = shouldCapture

        val info = serviceInfo.apply {
            flags = if (shouldCapture) {
                flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            } else {
                flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
            }
            if (Build.VERSION.SDK_INT >= 34) {
                motionEventSources = if (shouldCapture) InputDevice.SOURCE_JOYSTICK else 0
            }
        }
        serviceInfo = info
    }

    private fun prepareTouchMapping() {
        val seed = EfootballPresets.classicLandscape
        val saved = calibrationManager.loadSaved(seed)
        val profile = saved ?: seed
        mappingEngine.setProfile(profile)

        // Even with a saved profile, do not consume the controller in menus or splash screens.
        // The saved profile is a better visual seed; activation still waits for a live Classic HUD.
        configureInputCapture(null)
        mappingEngine.setActive(false)
        AppState.setMappingActive(false, "Auto-calibrating · waiting for Classic match HUD")

        scheduleCalibration(profile, initialDelayMs = 1400L)
    }

    private fun scheduleCalibration(seed: MappingProfile, initialDelayMs: Long) {
        if (calibrationScheduled || !shouldAutoCalibrate()) return
        calibrationScheduled = true
        handler.postDelayed({
            if (!shouldAutoCalibrate()) {
                calibrationScheduled = false
                return@postDelayed
            }

            calibrationManager.calibrate(seed) { calibrated, accepted ->
                calibrationScheduled = false
                if (!shouldAutoCalibrate()) return@calibrate

                if (accepted) {
                    val controller = AppState.state.value.controller ?: return@calibrate
                    mappingEngine.setProfile(calibrated)
                    configureInputCapture(controller)
                    mappingEngine.setActive(true)
                    calibrationAttempts = 0
                    hudMisses = 0

                    // Keep verifying at low frequency. When a match ends or a long menu/pause
                    // replaces the HUD, the mapper automatically releases the controller again.
                    scheduleCalibration(calibrated, initialDelayMs = HUD_VERIFY_INTERVAL_MS)
                } else {
                    calibrationAttempts++
                    if (mappingEngine.isActive()) {
                        hudMisses++
                        if (hudMisses >= HUD_MISS_LIMIT) {
                            configureInputCapture(null)
                            mappingEngine.setActive(false)
                            AppState.setMappingActive(false, "Auto-calibrating · waiting for Classic match HUD")
                            hudMisses = 0
                        }
                    }

                    // Menus/splash screens do not contain the Classic HUD. Keep checking without
                    // asking the user for coordinates or showing a mapping editor.
                    val nextDelay = if (mappingEngine.isActive()) {
                        HUD_VERIFY_INTERVAL_MS
                    } else if (calibrationAttempts < FAST_CALIBRATION_ATTEMPTS) {
                        2600L
                    } else {
                        calibrationAttempts = 0
                        AppState.setMappingActive(false, "Auto-calibrating · Classic HUD not detected yet")
                        5000L
                    }
                    scheduleCalibration(seed, initialDelayMs = nextDelay)
                }
            }
        }, initialDelayMs)
    }

    private fun shouldAutoCalibrate(): Boolean =
        Build.VERSION.SDK_INT >= 34 && efootballForeground && AppState.state.value.controller != null

    companion object {
        private const val FAST_CALIBRATION_ATTEMPTS = 8
        private const val HUD_MISS_LIMIT = 2
        private const val HUD_VERIFY_INTERVAL_MS = 6000L
    }
}
