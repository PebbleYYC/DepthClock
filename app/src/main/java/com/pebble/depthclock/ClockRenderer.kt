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
 * Uses the bundled Inter variable font (an OFL-licensed stand-in for SF Pro). The time weight
 * and a vertical stretch are adjustable to push Inter toward the tall iOS-clock look. If the
 * user loads their own font, it is used verbatim (no weight axis, no stretch tricks).
 */
object ClockRenderer {

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var fontsReady = false
    private var loadedFontKey: String? = null
    private var isCustomFont = false
    private var lastWeight = -1f

    private val tBounds = android.graphics.Rect()
    private val dBounds = android.graphics.Rect()

    private fun ensureFonts(context: Context) {
        val custom = Prefs.fontFile(context)
        val desired = if (custom.exists()) custom.absolutePath else "asset:inter"
        if (fontsReady && desired == loadedFontKey) return

        var cust = false
        val tf = if (desired == "asset:inter") {
            runCatching { Typeface.createFromAsset(context.assets, "fonts/Inter.ttf") }
                .getOrElse { Typeface.create("sans-serif", Typeface.NORMAL) }
        } else {
            runCatching { Typeface.createFromFile(custom) }.getOrNull()?.also { cust = true }
                ?: runCatching { Typeface.createFromAsset(context.assets, "fonts/Inter.ttf") }.getOrNull()
                ?: Typeface.create("sans-serif", Typeface.NORMAL)
        }
        timePaint.typeface = tf
        datePaint.typeface = tf
        if (cust) {
            timePaint.textScaleX = 1.0f
            runCatching { timePaint.fontVariationSettings = "" }
            runCatching { datePaint.fontVariationSettings = "" }
        } else {
            timePaint.textScaleX = 0.95f
            runCatching { datePaint.fontVariationSettings = "'wght' 500, 'opsz' 20" }
        }
        timePaint.letterSpacing = -0.02f
        isCustomFont = cust
        lastWeight = -1f
        loadedFontKey = desired
        fontsReady = true
    }

    /** Re-applies the variable weight only when it changes (building a Typeface per frame is costly). */
    private fun applyWeight(weight: Float) {
        if (isCustomFont) return
        val wq = weight.toInt().toFloat()
        if (wq == lastWeight) return
        runCatching { timePaint.fontVariationSettings = "'wght' ${wq.toInt()}, 'opsz' 32" }
        lastWeight = wq
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
        stretchY: Float = 1f,
        weight: Float = 560f,
        opacity: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ) {
        ensureFonts(context)
        applyWeight(weight)

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
        val stretch = stretchY.coerceIn(0.8f, 1.8f)
        val cx = w / 2f + offsetX
        val timeBaseline = h * yFraction + offsetY

        timePaint.color = color
        timePaint.alpha = (255 * a).toInt()
        timePaint.textSize = h * 0.148f * scale
        timePaint.setShadowLayer(h * 0.010f, 0f, h * 0.004f, Color.argb((110 * a).toInt(), 0, 0, 0))

        if (showDate) {
            datePaint.color = color
            datePaint.alpha = (235 * a).toInt()
            datePaint.textSize = h * 0.027f * scale
            datePaint.setShadowLayer(h * 0.007f, 0f, h * 0.003f, Color.argb((105 * a).toInt(), 0, 0, 0))

            val fmt = java.text.SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
            val dateStr = fmt.format(cal.time)

            // Measure the true top of the (stretched) time glyphs, then leave a clear fixed gap
            // above it so the date reads as its own line, well separated from the clock.
            timePaint.getTextBounds(time, 0, time.length, tBounds)
            val timeVisualTop = timeBaseline + tBounds.top * stretch
            datePaint.getTextBounds(dateStr, 0, dateStr.length, dBounds)
            val gap = h * 0.045f
            val dateBaseline = timeVisualTop - gap - dBounds.bottom
            canvas.drawText(dateStr, cx, dateBaseline, datePaint)
        }

        // Vertically stretch the time around its baseline for the tall iOS-clock proportion.
        val saved = canvas.save()
        canvas.scale(1f, stretch, cx, timeBaseline)
        canvas.drawText(time, cx, timeBaseline, timePaint)
        canvas.restoreToCount(saved)
    }
}
