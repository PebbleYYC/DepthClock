package com.pebble.depthclock

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ConfigScreen()
                }
            }
        }
    }
}

private val SWATCHES = listOf(
    Color.White, Color(0xFFFFF4D6), Color(0xFFBFE3FF),
    Color(0xFFFFC6D3), Color(0xFFC9F7D4), Color(0xFF111318)
)

@Composable
private fun ConfigScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = remember { Prefs.prefs(ctx) }

    var bg by remember { mutableStateOf<Bitmap?>(loadFile(Prefs.bgFile(ctx).absolutePath)) }
    var fg by remember { mutableStateOf<Bitmap?>(loadFile(Prefs.fgFile(ctx).absolutePath)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    var clockColor by remember { mutableStateOf(Color(p.getInt(Prefs.KEY_CLOCK_COLOR, Color.White.toArgb()))) }
    var clockY by remember { mutableFloatStateOf(p.getFloat(Prefs.KEY_CLOCK_Y, 0.30f)) }
    var clockScale by remember { mutableFloatStateOf(p.getFloat(Prefs.KEY_CLOCK_SCALE, 1.0f)) }
    var showSeconds by remember { mutableStateOf(p.getBoolean(Prefs.KEY_SHOW_SECONDS, false)) }
    var showDate by remember { mutableStateOf(p.getBoolean(Prefs.KEY_SHOW_DATE, true)) }
    var parallax by remember { mutableStateOf(p.getBoolean(Prefs.KEY_PARALLAX, true)) }

    fun persist() {
        p.edit()
            .putInt(Prefs.KEY_CLOCK_COLOR, clockColor.toArgb())
            .putFloat(Prefs.KEY_CLOCK_Y, clockY)
            .putFloat(Prefs.KEY_CLOCK_SCALE, clockScale)
            .putBoolean(Prefs.KEY_SHOW_SECONDS, showSeconds)
            .putBoolean(Prefs.KEY_SHOW_DATE, showDate)
            .putBoolean(Prefs.KEY_PARALLAX, parallax)
            .apply()
        Prefs.bumpRevision(ctx)
    }

    // Save settings whenever a knob changes so a live wallpaper already running picks them up.
    LaunchedEffect(clockColor, clockY, clockScale, showSeconds, showDate, parallax) { persist() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; status = "Finding the subject…"
        scope.launch {
            try {
                val source = withContext(Dispatchers.IO) { ImageUtils.loadScaled(ctx, uri) }
                val cut = withContext(Dispatchers.Default) { Segmenter.cutout(source) }
                if (cut == null) {
                    status = "Couldn't find a clear subject — try a photo with a person or object in front."
                } else {
                    withContext(Dispatchers.IO) {
                        ImageUtils.saveJpeg(source, Prefs.bgFile(ctx))
                        ImageUtils.savePng(cut, Prefs.fgFile(ctx))
                    }
                    bg = source; fg = cut
                    Prefs.bumpRevision(ctx)
                    status = null
                }
            } catch (t: Throwable) {
                status = "Something went wrong: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Depth Clock", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Pick a photo with a clear subject. The clock is drawn behind it, so the subject pops in front — like iOS. Set it as your home-screen wallpaper.",
            style = MaterialTheme.typography.bodyMedium
        )

        // ---- Live preview ----
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 19.5f)
                .clip(RoundedCornerShape(24.dp))
                .align(Alignment.CenterHorizontally)
        ) {
            if (bg != null && fg != null) {
                PreviewCanvas(bg!!, fg!!, clockColor, clockY, clockScale, showSeconds, showDate)
            } else {
                Surface(color = Color(0xFF15171C), modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("No photo yet", color = Color(0xFF8A8F98))
                    }
                }
            }
            if (busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        status?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (bg == null) "Choose photo" else "Choose a different photo") }

        HorizontalDivider()

        Text("Clock colour", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SWATCHES.forEach { c ->
                FilterChip(
                    selected = c.toArgb() == clockColor.toArgb(),
                    onClick = { clockColor = c },
                    label = { Text(colorName(c)) }
                )
            }
        }

        LabeledSlider("Vertical position", clockY, 0.08f..0.6f) { clockY = it }
        LabeledSlider("Size", clockScale, 0.6f..1.6f) { clockScale = it }

        ToggleRow("Show seconds", showSeconds) { showSeconds = it }
        ToggleRow("Show date", showDate) { showDate = it }
        ToggleRow("Tilt parallax", parallax) { parallax = it }

        HorizontalDivider()

        Button(
            onClick = { persist(); launchWallpaperPicker(ctx) },
            enabled = bg != null && fg != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Set as wallpaper") }

        Text(
            "Tip: on the wallpaper picker, choose Home screen. The lock screen keeps Android's own clock on top, so this looks best on the home screen.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8A8F98)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PreviewCanvas(
    bg: Bitmap, fg: Bitmap, clockColor: Color,
    clockY: Float, clockScale: Float, showSeconds: Boolean, showDate: Boolean
) {
    val ctx = LocalContext.current
    // Recompose every second so the preview clock ticks.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    val bgImg = remember(bg) { bg.asImageBitmap() }
    val fgImg = remember(fg) { fg.asImageBitmap() }

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // cover-scale source into the view
        val scale = maxOf(w / bg.width, h / bg.height)
        val dw = bg.width * scale
        val dh = bg.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        val dst = androidx.compose.ui.geometry.Rect(left, top, left + dw, top + dh)

        drawImage(
            bgImg,
            dstOffset = androidx.compose.ui.unit.IntOffset(dst.left.toInt(), dst.top.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt())
        )
        drawContext.canvas.nativeCanvas.apply {
            ClockRenderer.draw(this, w, h, clockColor.toArgb(), clockY, clockScale, showSeconds, showDate, now, DateFormat.is24HourFormat(ctx))
        }
        drawImage(
            fgImg,
            dstOffset = androidx.compose.ui.unit.IntOffset(dst.left.toInt(), dst.top.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt())
        )
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun colorName(c: Color): String = when (c.toArgb()) {
    Color.White.toArgb() -> "White"
    0xFFFFF4D6.toInt() -> "Warm"
    0xFFBFE3FF.toInt() -> "Ice"
    0xFFFFC6D3.toInt() -> "Rose"
    0xFFC9F7D4.toInt() -> "Mint"
    0xFF111318.toInt() -> "Ink"
    else -> "Colour"
}

private fun loadFile(path: String): Bitmap? =
    try { BitmapFactory.decodeFile(path) } catch (_: Throwable) { null }

private fun launchWallpaperPicker(ctx: Context) {
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(ctx, DepthWallpaperService::class.java)
        )
    }
    try {
        ctx.startActivity(intent)
    } catch (_: Throwable) {
        // Fallback: generic live wallpaper chooser
        ctx.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
    }
}
