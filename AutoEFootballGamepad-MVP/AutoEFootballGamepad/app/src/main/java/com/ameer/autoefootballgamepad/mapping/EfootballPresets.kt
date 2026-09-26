package com.ameer.autoefootballgamepad.mapping

/**
 * Normalized landscape presets used only as a safe starting point.
 * AutoCalibrationManager refines them from an eFootball screenshot before play when possible.
 * These coordinates are intentionally kept in code rather than exposed as a user mapping editor.
 */
object EfootballPresets {
    val classicLandscape = MappingProfile(
        name = "eFootball Classic - Adaptive",
        points = mapOf(
            TouchTarget.LEFT_STICK to NormalizedPoint(0.155f, 0.775f),
            // Position-stable 2x2 action grid. eFootball changes the labels between attack/defense.
            // South/A = bottom-left (Pass on attack / Switch on defence).
            TouchTarget.FACE_SOUTH to NormalizedPoint(0.705f, 0.815f),
            // East/B = bottom-right (Dash).
            TouchTarget.FACE_EAST to NormalizedPoint(0.925f, 0.815f),
            // West/X = top-right (Shoot/Tackle style action).
            TouchTarget.FACE_WEST to NormalizedPoint(0.925f, 0.605f),
            // North/Y = top-left (Through/Pressure style action).
            TouchTarget.FACE_NORTH to NormalizedPoint(0.770f, 0.620f),
            TouchTarget.DASH to NormalizedPoint(0.925f, 0.815f),
            TouchTarget.SWITCH_PLAYER to NormalizedPoint(0.705f, 0.815f)
        ),
        leftStickRadius = 0.095f,
        source = "bundled-preset"
    )
}
