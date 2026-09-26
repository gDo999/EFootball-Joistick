package com.ameer.autoefootballgamepad.mapping

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Centralized gesture compositor.
 *
 * AccessibilityService.dispatchGesture() cancels a gesture already in progress if another
 * independent gesture is dispatched. To allow running + passing/shooting at the same time,
 * every active virtual finger is composed into one GestureDescription frame and persistent
 * fingers are chained with continueStroke().
 */
class MultiTouchGestureEngine(
    private val service: AccessibilityService
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pointers = LinkedHashMap<TouchTarget, PointerState>()

    private var profile: PixelMappingProfile? = null
    private var dispatching = false
    private var frameScheduled = false
    private var enabled = false

    private val frameDurationMs = 42L
    private val minTapMs = 42L
    private val deadZone = 0.12f

    private data class PointerState(
        val target: TouchTarget,
        var desiredDown: Boolean = false,
        var x: Float = 0f,
        var y: Float = 0f,
        var lastX: Float = 0f,
        var lastY: Float = 0f,
        var previousStroke: GestureDescription.StrokeDescription? = null,
        var tapQueued: Boolean = false,
        var downSince: Long = 0L
    )

    fun updateProfile(profile: PixelMappingProfile) {
        this.profile = profile
        pointers.clear()
        profile.points.forEach { (target, point) ->
            pointers[target] = PointerState(
                target = target,
                x = point.x,
                y = point.y,
                lastX = point.x,
                lastY = point.y
            )
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) releaseAll()
    }

    fun setStick(xAxis: Float, yAxis: Float) {
        if (!enabled) return
        val p = profile ?: return
        val center = p.points[TouchTarget.LEFT_STICK] ?: return
        val state = pointers[TouchTarget.LEFT_STICK] ?: return

        val magnitude = hypot(xAxis.toDouble(), yAxis.toDouble()).toFloat()
        if (magnitude < deadZone) {
            state.desiredDown = false
            scheduleFrame()
            return
        }

        val scale = if (magnitude > 1f) 1f / magnitude else 1f
        state.x = center.x + xAxis * scale * p.leftStickRadiusPx
        state.y = center.y + yAxis * scale * p.leftStickRadiusPx
        if (!state.desiredDown) {
            state.lastX = center.x
            state.lastY = center.y
            state.downSince = SystemClock.uptimeMillis()
        }
        state.desiredDown = true
        scheduleFrame()
    }

    fun setButton(target: TouchTarget, down: Boolean) {
        if (!enabled) return
        val point = profile?.points?.get(target) ?: return
        val state = pointers.getOrPut(target) {
            PointerState(target = target, x = point.x, y = point.y, lastX = point.x, lastY = point.y)
        }
        state.x = point.x
        state.y = point.y

        if (down) {
            if (!state.desiredDown) state.downSince = SystemClock.uptimeMillis()
            state.desiredDown = true
        } else {
            // Do not lose very fast button taps that happen before the next compositor frame.
            if (state.previousStroke == null && state.desiredDown) {
                state.tapQueued = true
            }
            state.desiredDown = false
        }
        scheduleFrame()
    }

    fun releaseAll() {
        pointers.values.forEach { it.desiredDown = false }
        scheduleFrame()
    }

    private fun scheduleFrame(delayMs: Long = 0L) {
        if (frameScheduled) return
        frameScheduled = true
        mainHandler.postDelayed({
            frameScheduled = false
            dispatchFrame()
        }, delayMs)
    }

    private fun dispatchFrame() {
        if (dispatching) {
            scheduleFrame(4L)
            return
        }

        val builder = GestureDescription.Builder()
        val updates = mutableListOf<StrokeUpdate>()
        var strokeCount = 0
        val now = SystemClock.uptimeMillis()

        for (state in pointers.values) {
            val previous = state.previousStroke
            when {
                state.desiredDown -> {
                    val path = path(state.lastX, state.lastY, state.x, state.y)
                    val next = if (previous == null) {
                        GestureDescription.StrokeDescription(path, 0L, frameDurationMs, true)
                    } else {
                        previous.continueStroke(path, 0L, frameDurationMs, true)
                    }
                    builder.addStroke(next)
                    updates += StrokeUpdate(state, next, state.x, state.y, keep = true)
                    strokeCount++
                }

                previous != null -> {
                    // Respect a minimum physical press time for digital actions.
                    val elapsed = now - state.downSince
                    if (state.target != TouchTarget.LEFT_STICK && elapsed < minTapMs) {
                        state.desiredDown = true
                        scheduleFrame(minTapMs - elapsed)
                        continue
                    }
                    val path = path(state.lastX, state.lastY, state.lastX, state.lastY)
                    val end = previous.continueStroke(path, 0L, 1L, false)
                    builder.addStroke(end)
                    updates += StrokeUpdate(state, null, state.lastX, state.lastY, keep = false)
                    strokeCount++
                }

                state.tapQueued -> {
                    val p = Path().apply { moveTo(state.x, state.y) }
                    val tap = GestureDescription.StrokeDescription(p, 0L, minTapMs, false)
                    builder.addStroke(tap)
                    state.tapQueued = false
                    updates += StrokeUpdate(state, null, state.x, state.y, keep = false)
                    strokeCount++
                }
            }

            if (strokeCount >= GestureDescription.getMaxStrokeCount()) break
        }

        if (strokeCount == 0) return

        dispatching = true
        val accepted = service.dispatchGesture(
            builder.build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    dispatching = false
                    updates.forEach { u ->
                        u.state.previousStroke = if (u.keep) u.nextStroke else null
                        u.state.lastX = u.endX
                        u.state.lastY = u.endY
                    }
                    if (needsAnotherFrame()) scheduleFrame()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    dispatching = false
                    // A cancellation breaks continuation identity. Restart any desired fingers.
                    updates.forEach { u -> u.state.previousStroke = null }
                    pointers.values.forEach { s ->
                        if (s.desiredDown) {
                            val base = profile?.points?.get(s.target)
                            if (s.target == TouchTarget.LEFT_STICK && base != null) {
                                s.lastX = base.x
                                s.lastY = base.y
                            } else {
                                s.lastX = s.x
                                s.lastY = s.y
                            }
                        }
                    }
                    if (needsAnotherFrame()) scheduleFrame(8L)
                }
            },
            mainHandler
        )

        if (!accepted) {
            dispatching = false
            updates.forEach { it.state.previousStroke = null }
        }
    }

    private fun needsAnotherFrame(): Boolean = pointers.values.any {
        it.desiredDown || it.previousStroke != null || it.tapQueued
    }

    private fun path(fromX: Float, fromY: Float, toX: Float, toY: Float): Path = Path().apply {
        moveTo(fromX.coerceAtLeast(0f), fromY.coerceAtLeast(0f))
        if (abs(fromX - toX) > 0.01f || abs(fromY - toY) > 0.01f) {
            lineTo(toX.coerceAtLeast(0f), toY.coerceAtLeast(0f))
        }
    }

    private data class StrokeUpdate(
        val state: PointerState,
        val nextStroke: GestureDescription.StrokeDescription?,
        val endX: Float,
        val endY: Float,
        val keep: Boolean
    )
}
