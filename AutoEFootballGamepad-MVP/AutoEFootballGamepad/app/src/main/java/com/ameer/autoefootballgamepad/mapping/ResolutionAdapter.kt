package com.ameer.autoefootballgamepad.mapping

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import kotlin.math.min

class ResolutionAdapter(context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)

    data class DisplayInfo(
        val width: Int,
        val height: Int,
        val landscape: Boolean,
        val aspectRatio: Float
    )

    fun displayInfo(): DisplayInfo {
        val bounds: Rect = if (Build.VERSION.SDK_INT >= 30) {
            windowManager.maximumWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            Rect(0, 0, windowManager.defaultDisplay.width, windowManager.defaultDisplay.height)
        }
        val w = bounds.width()
        val h = bounds.height()
        return DisplayInfo(w, h, w >= h, maxOf(w, h).toFloat() / min(w, h).coerceAtLeast(1))
    }

    fun toPixels(profile: MappingProfile): PixelMappingProfile {
        val d = displayInfo()
        val points = profile.points.mapValues { (_, p) ->
            PixelPoint(p.x * d.width, p.y * d.height)
        }
        return PixelMappingProfile(
            name = profile.name,
            points = points,
            leftStickRadiusPx = profile.leftStickRadius * min(d.width, d.height),
            width = d.width,
            height = d.height,
            source = profile.source
        )
    }
}
