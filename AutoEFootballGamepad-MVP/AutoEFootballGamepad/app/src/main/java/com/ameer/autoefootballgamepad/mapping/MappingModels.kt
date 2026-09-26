package com.ameer.autoefootballgamepad.mapping

data class NormalizedPoint(val x: Float, val y: Float)

enum class TouchTarget {
    LEFT_STICK,
    FACE_SOUTH,
    FACE_EAST,
    FACE_WEST,
    FACE_NORTH,
    DASH,
    SWITCH_PLAYER
}

data class MappingProfile(
    val name: String,
    val points: Map<TouchTarget, NormalizedPoint>,
    val leftStickRadius: Float,
    val source: String = "preset"
)

data class PixelPoint(val x: Float, val y: Float)

data class PixelMappingProfile(
    val name: String,
    val points: Map<TouchTarget, PixelPoint>,
    val leftStickRadiusPx: Float,
    val width: Int,
    val height: Int,
    val source: String
)
