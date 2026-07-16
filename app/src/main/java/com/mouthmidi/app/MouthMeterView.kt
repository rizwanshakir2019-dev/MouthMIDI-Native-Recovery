package com.mouthmidi.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class MouthMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var value = 0f

    private var lastUpdateTime = 0L
    private val updateInterval = 33L

    fun setValue(cc: Int) {

        val now = System.currentTimeMillis()

        if (now - lastUpdateTime < updateInterval) {
            return
        }

        lastUpdateTime = now

        value = cc.coerceIn(0, 127).toFloat()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {

        val w = width.toFloat()
        val h = height.toFloat()

        paint.color = Color.rgb(50,50,50)

        canvas.drawRoundRect(
            0f,0f,w,h,
            18f,18f,
            paint
        )


        markerPaint.color = Color.argb(70, 255, 255, 255)
        markerPaint.strokeWidth = 1f

        for (level in listOf(0.25f, 0.5f, 0.75f)) {
            val y = h - (h * level)

            canvas.drawLine(
                0f,
                y,
                w,
                y,
                markerPaint
            )
        }


        val fill = h * value / 127f

        paint.color = when {
            value < 45 ->
                Color.rgb(0,210,106)

            value < 90 ->
                Color.rgb(255,170,0)

            else ->
                Color.rgb(255,60,80)
        }


        canvas.drawRoundRect(
            0f,
            h-fill,
            w,
            h,
            18f,
            18f,
            paint
        )
    }
}
