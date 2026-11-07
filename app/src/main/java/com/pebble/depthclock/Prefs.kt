package com.pebble.depthclock

import android.content.Context
import java.io.File

/**
 * Single source of truth for wallpaper configuration, backed by SharedPreferences.
 * Bitmaps themselves live as files in filesDir; we only store their paths here.
 */
object Prefs {
    private const val NAME = "depthclock"

    const val KEY_BG = "bg_path"
    const val KEY_FG = "fg_path"
    const val KEY_CLOCK_COLOR = "clock_color"
    const val KEY_CLOCK_Y = "clock_y"          // 0f..1f fraction of height for the clock baseline
    const val KEY_CLOCK_SCALE = "clock_scale"  // relative size multiplier
    const val KEY_CLOCK_ALPHA = "clock_alpha"  // 0f..1f clock opacity
    const val KEY_SHOW_SECONDS = "show_seconds"
    const val KEY_SHOW_DATE = "show_date"
    const val KEY_PARALLAX = "parallax"
    const val KEY_REV = "revision"             // bumped whenever config changes, so the engine reloads

    const val BG_FILE = "background.jpg"
    const val FG_FILE = "foreground.png"

    fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun bgFile(ctx: Context) = File(ctx.filesDir, BG_FILE)
    fun fgFile(ctx: Context) = File(ctx.filesDir, FG_FILE)

    fun hasImage(ctx: Context) = bgFile(ctx).exists() && fgFile(ctx).exists()

    fun bumpRevision(ctx: Context) {
        val p = prefs(ctx)
        p.edit().putInt(KEY_REV, p.getInt(KEY_REV, 0) + 1).apply()
    }
}
