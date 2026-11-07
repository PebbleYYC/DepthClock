package com.pebble.depthclock

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.util.Calendar
import java.util.Locale

/**
 * Draws the clock (and optional date) onto a Canvas. Shared by the config preview and the
 * live wallpaper engine so what you set up is exactly what you get.
 *
 * All sizing is relative to the surface height, so it looks the same on the preview card and
 * on the full screen.
 */
object ClockRenderer {

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    fun draw(
        canvas: Canvas,
        w: Float,
        h: Float,
        color: Int,
        yFraction: Float,
        scale: Float,
        showSeconds: Boolean,
        showDate: Boolean,
        nowMillis: Long,
        is24h: Boolean,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ) {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = if (is24h) cal.get(Calendar.HOUR_OF_DAY)
        else cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        val time = if (showSeconds)
            String.format(Locale.getDefault(), "%d:%02d:%02d", hour, minute, second)
        else
            String.format(Locale.getDefault(), "%d:%02d", hour, minute)

        val cx = w / 2f + offsetX
        val baseY = h * yFraction + offsetY

        timePaint.color = color
        timePaint.textSize = h * 0.11f * scale
        // Soft shadow so the clock stays legible over any photo.
        timePaint.setShadowLayer(h * 0.012f, 0f, h * 0.004f, Color.argb(130, 0, 0, 0))
        canvas.drawText(time, cx, baseY, timePaint)

        if (showDate) {
            val fmt = java.text.SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
            val dateStr = fmt.format(cal.time)
            datePaint.color = color
            datePaint.alpha = 220
            datePaint.textSize = h * 0.028f * scale
            datePaint.setShadowLayer(h * 0.008f, 0f, h * 0.003f, Color.argb(120, 0, 0, 0))
            canvas.drawText(dateStr, cx, baseY - h * 0.085f * scale, datePaint)
        }
    }
}
