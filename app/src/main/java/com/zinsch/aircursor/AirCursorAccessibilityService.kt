package com.zinsch.aircursor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class AirCursorAccessibilityService : AccessibilityService() {
    private var activeStroke: GestureDescription.StrokeDescription? = null
    private var lastX = 0f
    private var lastY = 0f

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { endDrag() }

    fun tap(x: Float, y: Float) {
        endDrag()
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 65))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun beginDrag(x: Float, y: Float) {
        endDrag()
        val path = Path().apply { moveTo(x, y); lineTo(x + 0.5f, y + 0.5f) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60, true)
        activeStroke = stroke
        lastX = x
        lastY = y
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun moveDrag(x: Float, y: Float) {
        val current = activeStroke ?: return
        val path = Path().apply {
            moveTo(lastX, lastY)
            lineTo(x, y)
        }
        val next = current.continueStroke(path, 0, 45, true)
        activeStroke = next
        lastX = x
        lastY = y
        dispatchGesture(GestureDescription.Builder().addStroke(next).build(), null, null)
    }

    fun endDrag() {
        val current = activeStroke ?: return
        val path = Path().apply {
            moveTo(lastX, lastY)
            lineTo(lastX + 0.1f, lastY + 0.1f)
        }
        val finalStroke = current.continueStroke(path, 0, 40, false)
        activeStroke = null
        dispatchGesture(GestureDescription.Builder().addStroke(finalStroke).build(), null, null)
    }

    fun scroll(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        endDrag()
        val path = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()
        dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile var instance: AirCursorAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        endDrag()
        if (instance === this) instance = null
        super.onDestroy()
    }
}
