package com.ameer.autoefootballgamepad.controller

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.ameer.autoefootballgamepad.mapping.AutomaticMappingEngine
import com.ameer.autoefootballgamepad.mapping.TouchTarget
import kotlin.math.abs
import kotlin.math.max

class GamepadInputManager(
    private val mappingEngine: AutomaticMappingEngine
) {
    private var dpadX = 0f
    private var dpadY = 0f
    private var rightTriggerDown = false
    private var leftTriggerDown = false

    fun onKeyEvent(event: KeyEvent): Boolean {
        val isController = event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        if (!isController) return false

        val down = event.action == KeyEvent.ACTION_DOWN
        val up = event.action == KeyEvent.ACTION_UP
        if (!down && !up) return false

        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> mappingEngine.button(TouchTarget.FACE_SOUTH, down)
            KeyEvent.KEYCODE_BUTTON_B -> mappingEngine.button(TouchTarget.FACE_EAST, down)
            KeyEvent.KEYCODE_BUTTON_X -> mappingEngine.button(TouchTarget.FACE_WEST, down)
            KeyEvent.KEYCODE_BUTTON_Y -> mappingEngine.button(TouchTarget.FACE_NORTH, down)
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2 -> mappingEngine.button(TouchTarget.DASH, down)
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L2 -> mappingEngine.button(TouchTarget.SWITCH_PLAYER, down)
            KeyEvent.KEYCODE_DPAD_LEFT -> updateDpad(x = if (down) -1f else 0f, y = null)
            KeyEvent.KEYCODE_DPAD_RIGHT -> updateDpad(x = if (down) 1f else 0f, y = null)
            KeyEvent.KEYCODE_DPAD_UP -> updateDpad(x = null, y = if (down) -1f else 0f)
            KeyEvent.KEYCODE_DPAD_DOWN -> updateDpad(x = null, y = if (down) 1f else 0f)
            else -> return false
        }
        return true
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        val joystick = event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!joystick) return false

        val device = event.device ?: return false
        val stickX = centeredAxis(event, device, MotionEvent.AXIS_X)
        val stickY = centeredAxis(event, device, MotionEvent.AXIS_Y)
        val hatX = centeredAxis(event, device, MotionEvent.AXIS_HAT_X)
        val hatY = centeredAxis(event, device, MotionEvent.AXIS_HAT_Y)

        // Some generic pads expose the D-pad as HAT axes rather than KeyEvents.
        if (abs(stickX) > 0.01f || abs(stickY) > 0.01f) {
            mappingEngine.move(stickX, stickY)
        } else if (abs(hatX) > 0.01f || abs(hatY) > 0.01f) {
            mappingEngine.move(hatX, hatY)
        } else {
            mappingEngine.move(0f, 0f)
        }

        // Trigger reporting varies by Android controller driver. Support the common alternate axes.
        val rightTrigger = max(
            positiveAxis(event, device, MotionEvent.AXIS_RTRIGGER),
            positiveAxis(event, device, MotionEvent.AXIS_GAS)
        )
        val leftTrigger = max(
            positiveAxis(event, device, MotionEvent.AXIS_LTRIGGER),
            positiveAxis(event, device, MotionEvent.AXIS_BRAKE)
        )
        updateTrigger(right = true, pressed = rightTrigger >= TRIGGER_THRESHOLD)
        updateTrigger(right = false, pressed = leftTrigger >= TRIGGER_THRESHOLD)
        return true
    }

    private fun centeredAxis(event: MotionEvent, device: InputDevice, axis: Int): Float {
        val range = device.getMotionRange(axis, event.source) ?: return 0f
        val value = event.getAxisValue(axis)
        val flat = maxOf(range.flat, 0.08f)
        if (abs(value) <= flat) return 0f
        return value.coerceIn(-1f, 1f)
    }

    private fun positiveAxis(event: MotionEvent, device: InputDevice, axis: Int): Float {
        val range = device.getMotionRange(axis, event.source) ?: return 0f
        val value = event.getAxisValue(axis)
        val normalized = if (range.max > range.min) {
            ((value - range.min) / (range.max - range.min)).coerceIn(0f, 1f)
        } else {
            value.coerceIn(0f, 1f)
        }
        return if (normalized <= maxOf(range.flat, 0.05f)) 0f else normalized
    }

    private fun updateTrigger(right: Boolean, pressed: Boolean) {
        if (right) {
            if (pressed != rightTriggerDown) {
                rightTriggerDown = pressed
                mappingEngine.button(TouchTarget.DASH, pressed)
            }
        } else if (pressed != leftTriggerDown) {
            leftTriggerDown = pressed
            mappingEngine.button(TouchTarget.SWITCH_PLAYER, pressed)
        }
    }

    private fun updateDpad(x: Float?, y: Float?) {
        if (x != null) dpadX = x
        if (y != null) dpadY = y
        mappingEngine.move(dpadX, dpadY)
    }

    private companion object {
        const val TRIGGER_THRESHOLD = 0.55f
    }
}
