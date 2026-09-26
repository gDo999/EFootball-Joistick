package com.ameer.autoefootballgamepad.detection

import android.graphics.Bitmap
import com.ameer.autoefootballgamepad.mapping.MappingProfile
import com.ameer.autoefootballgamepad.mapping.NormalizedPoint
import com.ameer.autoefootballgamepad.mapping.TouchTarget
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight on-device visual detector for the MVP.
 *
 * It uses each bundled preset point only as a search prior, then looks for a nearby high-contrast
 * circular/control-like region. This makes the mapping scale-independent and tolerant to modest
 * UI shifts. It does not upload screenshots and does not use OCR/network services.
 */
class EfootballScreenDetector {

    enum class ControlLayout { CLASSIC, UNKNOWN }

    data class DetectionResult(
        val layout: ControlLayout,
        val refinedProfile: MappingProfile,
        val confidence: Float,
        val foundTargets: Int
    )

    fun detect(bitmap: Bitmap, seed: MappingProfile): DetectionResult {
        if (bitmap.width < 400 || bitmap.height < 250) {
            return DetectionResult(ControlLayout.UNKNOWN, seed, 0f, 0)
        }

        val refined = seed.points.toMutableMap()
        val scores = mutableListOf<Float>()
        var found = 0

        // Calibrate physical on-screen controls only once. DASH/FACE_EAST and
        // SWITCH_PLAYER/FACE_SOUTH intentionally share the same eFootball button positions.
        val physicalTargets = listOf(
            TouchTarget.LEFT_STICK,
            TouchTarget.FACE_SOUTH,
            TouchTarget.FACE_EAST,
            TouchTarget.FACE_WEST,
            TouchTarget.FACE_NORTH
        )

        physicalTargets.forEach { target ->
            val point = seed.points[target] ?: return@forEach
            val window = if (target == TouchTarget.LEFT_STICK) 0.075f else 0.055f
            val hit = refineNearby(bitmap, point, window)
            if (hit.score >= 0.20f) {
                refined[target] = hit.point
                found++
            }
            scores += hit.score
        }

        // Keep semantic aliases pinned to the same physical button after visual refinement.
        refined[TouchTarget.FACE_EAST]?.let { refined[TouchTarget.DASH] = it }
        refined[TouchTarget.FACE_SOUTH]?.let { refined[TouchTarget.SWITCH_PLAYER] = it }

        val faceTargets = physicalTargets.filter { it != TouchTarget.LEFT_STICK }
        val faceScore = faceTargets.map { target ->
            val expected = seed.points[target] ?: return@map 0f
            refineNearby(bitmap, expected, 0.055f).score
        }.average().toFloat()

        val overall = if (scores.isEmpty()) 0f else scores.average().toFloat().coerceIn(0f, 1f)
        val layout = if (faceScore >= 0.18f && found >= 3) ControlLayout.CLASSIC else ControlLayout.UNKNOWN

        return DetectionResult(
            layout = layout,
            refinedProfile = seed.copy(
                name = seed.name + " / Auto-calibrated",
                points = refined,
                source = "visual-auto-calibration"
            ),
            confidence = overall,
            foundTargets = found
        )
    }

    private data class Hit(val point: NormalizedPoint, val score: Float)

    private fun refineNearby(bitmap: Bitmap, expected: NormalizedPoint, searchRadius: Float): Hit {
        val w = bitmap.width
        val h = bitmap.height
        val cx = (expected.x * w).toInt()
        val cy = (expected.y * h).toInt()
        val rx = max(18, (searchRadius * w).toInt())
        val ry = max(18, (searchRadius * h).toInt())
        val step = max(5, min(w, h) / 160)
        val probe = max(6, min(w, h) / 95)

        var bestScore = -1f
        var bestX = cx
        var bestY = cy

        val x0 = (cx - rx).coerceIn(probe + 1, w - probe - 2)
        val x1 = (cx + rx).coerceIn(probe + 1, w - probe - 2)
        val y0 = (cy - ry).coerceIn(probe + 1, h - probe - 2)
        val y1 = (cy + ry).coerceIn(probe + 1, h - probe - 2)

        var y = y0
        while (y <= y1) {
            var x = x0
            while (x <= x1) {
                val score = controlContrastScore(bitmap, x, y, probe)
                if (score > bestScore) {
                    bestScore = score
                    bestX = x
                    bestY = y
                }
                x += step
            }
            y += step
        }

        return Hit(
            point = NormalizedPoint(bestX.toFloat() / w, bestY.toFloat() / h),
            score = bestScore.coerceIn(0f, 1f)
        )
    }

    /**
     * Compares center luminance with four ring samples and adds a local edge term.
     * Transparent game controls generally create a stable ring/center contrast even across themes.
     */
    private fun controlContrastScore(bitmap: Bitmap, x: Int, y: Int, r: Int): Float {
        val center = meanLuma(bitmap, x, y, max(2, r / 3))
        val left = meanLuma(bitmap, x - r, y, max(2, r / 4))
        val right = meanLuma(bitmap, x + r, y, max(2, r / 4))
        val top = meanLuma(bitmap, x, y - r, max(2, r / 4))
        val bottom = meanLuma(bitmap, x, y + r, max(2, r / 4))
        val ring = (left + right + top + bottom) / 4f

        val contrast = abs(center - ring) / 255f
        val symmetryPenalty = (abs(left - right) + abs(top - bottom)) / (2f * 255f)
        val raw = contrast * 1.8f + (0.35f - symmetryPenalty.coerceAtMost(0.35f))
        return raw.coerceIn(0f, 1f)
    }

    private fun meanLuma(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): Float {
        var sum = 0f
        var count = 0
        val stride = max(1, radius / 3)
        var y = (cy - radius).coerceAtLeast(0)
        val yMax = (cy + radius).coerceAtMost(bitmap.height - 1)
        while (y <= yMax) {
            var x = (cx - radius).coerceAtLeast(0)
            val xMax = (cx + radius).coerceAtMost(bitmap.width - 1)
            while (x <= xMax) {
                val c = bitmap.getPixel(x, y)
                val rr = (c shr 16) and 0xFF
                val gg = (c shr 8) and 0xFF
                val bb = c and 0xFF
                sum += rr * 0.2126f + gg * 0.7152f + bb * 0.0722f
                count++
                x += stride
            }
            y += stride
        }
        return if (count == 0) 0f else sum / count
    }
}
