package com.zinsch.aircursor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.view.View

class CursorOverlay(context: Context) : View(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190, 150, 255)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x338F5BFF
        style = Paint.Style.FILL
    }

    @Volatile var cursorX = -100f
    @Volatile var cursorY = -100f
    @Volatile var mode = "POINT"

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = cursorX
        val y = cursorY
        if (x < 0f || y < 0f) return
        val r = when (mode) {
            "DRAG" -> 18f
            "SCROLL" -> 23f
            "PINCH" -> 15f
            else -> 20f
        }
        canvas.drawCircle(x, y, r + 9f, glow)
        canvas.drawCircle(x, y, r, ring)
        canvas.drawCircle(x, y, 4.5f, dot)
    }
}
