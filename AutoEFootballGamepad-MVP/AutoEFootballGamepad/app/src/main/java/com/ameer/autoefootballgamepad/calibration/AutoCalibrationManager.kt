package com.ameer.autoefootballgamepad.calibration

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import com.ameer.autoefootballgamepad.core.AppState
import com.ameer.autoefootballgamepad.detection.EfootballScreenDetector
import com.ameer.autoefootballgamepad.mapping.MappingProfile
import com.ameer.autoefootballgamepad.mapping.NormalizedPoint
import com.ameer.autoefootballgamepad.mapping.ResolutionAdapter
import com.ameer.autoefootballgamepad.mapping.TouchTarget
import com.ameer.autoefootballgamepad.util.ErrorLogger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AutoCalibrationManager(
    private val service: AccessibilityService,
    private val detector: EfootballScreenDetector,
    private val resolutionAdapter: ResolutionAdapter,
    private val logger: ErrorLogger
) {
    private val prefs = service.getSharedPreferences("auto_calibration", 0)
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "efootball-auto-calibration").apply { isDaemon = true }
    }

    @Volatile
    private var inFlight = false

    fun loadSaved(seed: MappingProfile): MappingProfile? {
        val d = resolutionAdapter.displayInfo()
        val prefix = keyPrefix(d.width, d.height)
        if (!prefs.getBoolean("${prefix}_valid", false)) return null

        val points = seed.points.toMutableMap()
        TouchTarget.entries.forEach { target ->
            val xKey = "${prefix}_${target.name}_x"
            val yKey = "${prefix}_${target.name}_y"
            if (prefs.contains(xKey) && prefs.contains(yKey)) {
                points[target] = NormalizedPoint(
                    prefs.getFloat(xKey, seed.points[target]?.x ?: 0f),
                    prefs.getFloat(yKey, seed.points[target]?.y ?: 0f)
                )
            }
        }
        return seed.copy(
            name = seed.name + " / Saved calibration",
            points = points,
            source = "saved-auto-calibration"
        )
    }

    fun calibrate(seed: MappingProfile, onResult: (MappingProfile, Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 30 || inFlight) {
            onResult(seed, false)
            return
        }
        inFlight = true

        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            worker,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    try {
                        val buffer = screenshot.hardwareBuffer
                        val bitmap = try {
                            val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            try {
                                hardware?.copy(Bitmap.Config.ARGB_8888, false)
                            } finally {
                                hardware?.recycle()
                            }
                        } finally {
                            buffer.close()
                        }

                        if (bitmap == null) {
                            postFailure(seed, "Screenshot conversion failed", onResult)
                            return
                        }

                        val result = try {
                            detector.detect(bitmap, seed)
                        } finally {
                            bitmap.recycle()
                        }

                        val accepted = result.confidence >= 0.20f && result.foundTargets >= 3
                        if (accepted) save(result.refinedProfile)

                        service.mainExecutor.execute {
                            val layoutName = result.layout.name.lowercase()
                                .replaceFirstChar { it.uppercase() }
                            AppState.setCalibration(result.confidence, layoutName)

                            inFlight = false
                            onResult(if (accepted) result.refinedProfile else seed, accepted)
                        }
                    } catch (t: Throwable) {
                        postFailure(seed, "Auto calibration failed: ${t.message}", onResult)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    postFailure(
                        seed,
                        "Accessibility screenshot failed with code $errorCode",
                        onResult
                    )
                }
            }
        )
    }

    fun shutdown() {
        worker.shutdownNow()
    }

    private fun postFailure(
        seed: MappingProfile,
        message: String,
        onResult: (MappingProfile, Boolean) -> Unit
    ) {
        service.mainExecutor.execute {
            inFlight = false
            logger.log(message)
            AppState.setError(message)
            onResult(seed, false)
        }
    }

    private fun save(profile: MappingProfile) {
        val d = resolutionAdapter.displayInfo()
        val prefix = keyPrefix(d.width, d.height)
        prefs.edit().apply {
            putBoolean("${prefix}_valid", true)
            profile.points.forEach { (target, point) ->
                putFloat("${prefix}_${target.name}_x", point.x)
                putFloat("${prefix}_${target.name}_y", point.y)
            }
        }.apply()
    }

    private fun keyPrefix(width: Int, height: Int): String = "${width}x${height}_landscape"
}
