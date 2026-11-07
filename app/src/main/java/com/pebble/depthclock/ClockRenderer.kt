package com.pebble.depthclock

import android.content.Context
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
 * Uses the bundled Inter variable font (an OFL-licensed stand-in for SF Pro). The time is large,
 * slightly condensed and medium-weight to echo the iOS lock-screen clock; the date floats above.
 */
object ClockRenderer {

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var fontsReady = false

    private fun ensureFonts(context: Context) {
        if (fontsReady) return
        val inter = try {
            Typeface.createFromAsset(context.assets, "fonts/Inter.ttf")
        } catch (_: Throwable) {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
        timePaint.typeface = inter
        datePaint.typeface = inter
        runCatching { timePaint.fontVariationSettings = "'wght' 560, 'opsz' 32" }
        runCatching { datePaint.fontVariationSettings = "'wght' 520, 'opsz' 20" }
        // Slightly condensed + tight tracking → taller-looking numerals, closer to SF Pro.
        timePaint.textScaleX = 0.95f
        timePaint.letterSpacing = -0.02f
        fontsReady = true
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
        context: Context,
        opacity: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ) {
        ensureFonts(context)

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = if (is24h) cal.get(Calendar.HOUR_OF_DAY)
        else cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        val time = if (showSeconds)
            String.format(Locale.getDefault(), "%d:%02d:%02d", hour, minute, second)
        else
            String.format(Locale.getDefault(), "%d:%02d", hour, minute)

        val a = opacity.coerceIn(0f, 1f)
        val cx = w / 2f + offsetX
        val timeBaseline = h * yFraction + offsetY

        timePaint.color = color
        timePaint.alpha = (255 * a).toInt()
        timePaint.textSize = h * 0.158f * scale
        timePaint.setShadowLayer(h * 0.010f, 0f, h * 0.004f, Color.argb((110 * a).toInt(), 0, 0, 0))

        if (showDate) {
            datePaint.color = color
            datePaint.alpha = (235 * a).toInt()
            datePaint.textSize = h * 0.027f * scale
            datePaint.setShadowLayer(h * 0.007f, 0f, h * 0.003f, Color.argb((105 * a).toInt(), 0, 0, 0))

            val fmt = java.text.SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
            val dateStr = fmt.format(cal.time)

            // Sit the date clearly above the top of the time digits (iOS-style gap).
            val gap = h * 0.030f * scale
            val timeTop = timeBaseline + timePaint.fontMetrics.ascent
            val dateBaseline = timeTop - gap - datePaint.fontMetrics.descent
            canvas.drawText(dateStr, cx, dateBaseline, datePaint)
        }

        canvas.drawText(time, cx, timeBaseline, timePaint)
    }
}
