package com.pebble.depthclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.text.format.DateFormat
import android.view.Choreographer
import android.view.SurfaceHolder
import kotlin.math.abs

class DepthWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = DepthEngine()

    inner class DepthEngine : Engine(), SensorEventListener {

        private val handler = Handler(Looper.getMainLooper())
        private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
        private val rotationSensor by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) }

        private var bg: Bitmap? = null
        private var fg: Bitmap? = null

        private var width = 1
        private var height = 1
        private var visible = false

        // config
        private var clockColor = Color.WHITE
        private var clockY = 0.26f
        private var clockScale = 1.0f
        private var clockAlpha = 1.0f
        private var clockStretch = 1.2f
        private var clockWeight = 520f
        private var showSeconds = false
        private var showDate = true
        private var parallaxEnabled = true
        private var is24h = true
        private var revision = -1

        // parallax state
        private var baseRoll = Float.NaN
        private var basePitch = Float.NaN
        private var targetDx = 0f
        private var targetDy = 0f
        private var curDx = 0f
        private var curDy = 0f

        private var lastTimeKey = ""

        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val rotMatrix = FloatArray(9)
        private val orientation = FloatArray(3)

        private val timeTick = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = requestDraw()
        }

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!visible) return
                step()
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            loadConfig()
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                maybeReload()
                is24h = DateFormat.is24HourFormat(this@DepthWallpaperService)
                if (parallaxEnabled && rotationSensor != null) {
                    baseRoll = Float.NaN; basePitch = Float.NaN
                    sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
                }
                registerReceiver(timeTick, IntentFilter(Intent.ACTION_TIME_TICK))
                Choreographer.getInstance().postFrameCallback(frameCallback)
            } else {
                sensorManager.unregisterListener(this)
                runCatching { unregisterReceiver(timeTick) }
                Choreographer.getInstance().removeFrameCallback(frameCallback)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            width = w; height = h
            requestDraw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            visible = false
            sensorManager.unregisterListener(this)
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }

        override fun onDestroy() {
            super.onDestroy()
            runCatching { unregisterReceiver(timeTick) }
            bg?.recycle(); fg?.recycle()
        }

        // --- config / assets ---

        private fun loadConfig() {
            val p = Prefs.prefs(this@DepthWallpaperService)
            clockColor = p.getInt(Prefs.KEY_CLOCK_COLOR, Color.WHITE)
            clockY = p.getFloat(Prefs.KEY_CLOCK_Y, 0.26f)
            clockScale = p.getFloat(Prefs.KEY_CLOCK_SCALE, 1.0f)
            clockAlpha = p.getFloat(Prefs.KEY_CLOCK_ALPHA, 1.0f)
            clockStretch = p.getFloat(Prefs.KEY_CLOCK_STRETCH, 1.2f)
            clockWeight = p.getFloat(Prefs.KEY_CLOCK_WEIGHT, 520f)
            showSeconds = p.getBoolean(Prefs.KEY_SHOW_SECONDS, false)
            showDate = p.getBoolean(Prefs.KEY_SHOW_DATE, true)
            parallaxEnabled = p.getBoolean(Prefs.KEY_PARALLAX, true)
            revision = p.getInt(Prefs.KEY_REV, 0)
            loadBitmaps()
        }

        private fun loadBitmaps() {
            bg?.recycle(); fg?.recycle()
            bg = decode(Prefs.bgFile(this@DepthWallpaperService).absolutePath)
            fg = decode(Prefs.fgFile(this@DepthWallpaperService).absolutePath)
        }

        private fun decode(path: String): Bitmap? =
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()

        /** Reload if the user changed settings in the config screen while we were hidden. */
        private fun maybeReload() {
            val rev = Prefs.prefs(this@DepthWallpaperService).getInt(Prefs.KEY_REV, 0)
            if (rev != revision) loadConfig()
        }

        // --- frame loop ---

        private fun requestDraw() {
            if (visible) return // the running loop will pick it up
            drawFrame()
        }

        private fun step() {
            // ease current offsets toward target
            curDx += (targetDx - curDx) * 0.12f
            curDy += (targetDy - curDy) * 0.12f

            val key = timeKey()
            val moving = abs(targetDx - curDx) > 0.4f || abs(targetDy - curDy) > 0.4f
            if (moving || key != lastTimeKey) {
                lastTimeKey = key
                drawFrame()
            }
        }

        private fun timeKey(): String {
            val cal = java.util.Calendar.getInstance()
            return if (showSeconds)
                "${cal.get(java.util.Calendar.HOUR_OF_DAY)}:${cal.get(java.util.Calendar.MINUTE)}:${cal.get(java.util.Calendar.SECOND)}"
            else
                "${cal.get(java.util.Calendar.HOUR_OF_DAY)}:${cal.get(java.util.Calendar.MINUTE)}"
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                render(canvas)
            } finally {
                if (canvas != null) runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun render(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val background = bg
            val foreground = fg

            if (background == null || foreground == null) {
                canvas.drawColor(Color.parseColor("#15171C"))
                val now = System.currentTimeMillis()
                ClockRenderer.draw(canvas, w, h, clockColor, clockY, clockScale, showSeconds, showDate, now, is24h, this@DepthWallpaperService, clockStretch, clockWeight, clockAlpha)
                return
            }

            canvas.drawColor(Color.BLACK)

            // cover-scale plus a margin so parallax shifts never reveal an edge
            val cover = maxOf(w / background.width, h / background.height)
            val drawScale = cover * 1.08f
            val dw = background.width * drawScale
            val dh = background.height * drawScale
            val baseLeft = (w - dw) / 2f
            val baseTop = (h - dh) / 2f

            val maxX = (dw - w) / 2f
            val maxY = (dh - h) / 2f
            val dx = curDx.coerceIn(-maxX, maxX)
            val dy = curDy.coerceIn(-maxY, maxY)

            // depth factors: background drifts least, clock sits between, subject moves most
            val bgX = dx * 0.25f; val bgY = dy * 0.25f
            val ckX = dx * 0.55f; val ckY = dy * 0.55f
            val fgX = dx * 1.00f; val fgY = dy * 1.00f

            drawLayer(canvas, background, baseLeft + bgX, baseTop + bgY, dw, dh)

            val now = System.currentTimeMillis()
            ClockRenderer.draw(canvas, w, h, clockColor, clockY, clockScale, showSeconds, showDate, now, is24h, this@DepthWallpaperService, clockStretch, clockWeight, clockAlpha, ckX, ckY)

            drawLayer(canvas, foreground, baseLeft + fgX, baseTop + fgY, dw, dh)
        }

        private val dst = RectF()
        private fun drawLayer(canvas: Canvas, bmp: Bitmap, left: Float, top: Float, dw: Float, dh: Float) {
            dst.set(left, top, left + dw, top + dh)
            canvas.drawBitmap(bmp, null, dst, paint)
        }

        // --- sensors ---

        override fun onSensorChanged(event: SensorEvent) {
            if (!parallaxEnabled) return
            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
            SensorManager.getOrientation(rotMatrix, orientation)
            val pitch = orientation[1] // front/back tilt (radians)
            val roll = orientation[2]  // left/right tilt (radians)
            if (baseRoll.isNaN()) { baseRoll = roll; basePitch = pitch }

            val amp = height * 0.03f // max shift in px for a strong tilt
            // deltas in radians, clamped to a comfortable tilt range (~0.6 rad)
            val dRoll = ((roll - baseRoll)).coerceIn(-0.6f, 0.6f) / 0.6f
            val dPitch = ((pitch - basePitch)).coerceIn(-0.6f, 0.6f) / 0.6f
            targetDx = -dRoll * amp
            targetDy = dPitch * amp
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}
