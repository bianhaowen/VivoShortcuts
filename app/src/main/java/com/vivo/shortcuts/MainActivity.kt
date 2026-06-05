package com.vivo.shortcuts

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private const val TAG = "DisplayControl"

class MainActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var displayManager: DisplayManager
    private var displayListener: DisplayManager.DisplayListener? = null
    private val iwm by lazy { getIWindowManager() }

    // Observed by Compose
    val isRefreshRateHigh = mutableStateOf(false)
    val isResolutionHigh = mutableStateOf(false)

    // ── Lifecycle ──────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        if (!Settings.System.canWrite(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        }

        refreshDisplayStates()
        registerDisplayListener()

        setContent {
            MainScreen(
                audioManager = audioManager,
                isRefreshRateHigh = isRefreshRateHigh.value,
                isResolutionHigh = isResolutionHigh.value,
                onRefreshRateToggle = { setRefreshRate(it) },
                onResolutionToggle = { setResolution(it) }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        displayListener?.let { displayManager.unregisterDisplayListener(it) }
    }

    // ── Display State ──────────────────────────────────────────────

    private fun defaultDisplay(): Display? =
        displayManager.getDisplay(Display.DEFAULT_DISPLAY)

    private fun refreshDisplayStates() {
        val display = defaultDisplay() ?: return
        val current = display.mode
        val modes = display.supportedModes
        Log.d(TAG, "Current: ${current.physicalWidth}x${current.physicalHeight} @${current.refreshRate}Hz id=${current.modeId}")
        modes.forEachIndexed { i, m ->
            Log.d(TAG, "  Mode[$i]: ${m.physicalWidth}x${m.physicalHeight} @${m.refreshRate}Hz id=${m.modeId}")
        }

        // Refresh rate: read Vivo OriginOS system settings key
        // vivo_screen_refresh_rate_mode stores the current refresh rate setting (60/90/120)
        val vivoRR = try {
            Settings.Global.getInt(contentResolver, "vivo_screen_refresh_rate_mode")
        } catch (_: Exception) { -1 }
        if (vivoRR > 0) {
            val maxRR = 120  // X300 Pro max
            isRefreshRateHigh.value = vivoRR >= maxRR
            Log.d(TAG, "Refresh (vivo key): vivoRR=$vivoRR max=$maxRR high=${isRefreshRateHigh.value}")
        } else {
            // Fallback: use display mode refresh rate
            val rates = modes.map { it.refreshRate }.distinct()
            val maxRR = if (rates.size >= 2) rates.max() else (rates.firstOrNull() ?: 120f)
            val minRR = if (rates.size >= 2) rates.min() else 60f
            isRefreshRateHigh.value = current.refreshRate >= (maxRR + minRR) / 2f
            Log.d(TAG, "Refresh (mode): min=$minRR max=$maxRR cur=${current.refreshRate} high=${isRefreshRateHigh.value}")
        }

        // Resolution: use supportedModes if multiple distinct resolutions exist
        val resPx = modes.map { it.physicalWidth * it.physicalHeight }.distinct()
        if (resPx.size >= 2) {
            val maxPx = resPx.max()
            val minPx = resPx.min()
            val curPx = current.physicalWidth * current.physicalHeight
            isResolutionHigh.value = curPx >= (maxPx + minPx) / 2
            Log.d(TAG, "Resolution (modes): max=$maxPx min=$minPx cur=$curPx high=${isResolutionHigh.value}")
        } else {
            // Only one native mode (2800×1260) — detect low via forced size
            // display.mode.* always returns native values, so use actual display metrics
            val actualW = resources.displayMetrics.widthPixels
            val maxMode = modes.maxByOrNull { it.physicalWidth * it.physicalHeight }
            val nativeW = maxMode?.physicalWidth ?: 2800
            isResolutionHigh.value = actualW >= nativeW * 0.9f
            Log.d(TAG, "Resolution (forced): actualW=$actualW nativeW=$nativeW high=${isResolutionHigh.value}")
        }
    }

    // ── Set Refresh Rate ───────────────────────────────────────────

    private fun setRefreshRate(high: Boolean) {
        val display = defaultDisplay() ?: return
        val modes = display.supportedModes
        val rates = modes.map { it.refreshRate }.distinct()

        val maxRR: Float
        val minRR: Float
        if (rates.size >= 2) {
            maxRR = rates.max()
            minRR = rates.min()
        } else {
            maxRR = rates.firstOrNull() ?: 120f
            minRR = 60f
        }
        val targetRR = if (high) maxRR else minRR
        Log.d(TAG, "setRefreshRate high=$high target=$targetRR (min=$minRR max=$maxRR)")

        // Try to find a display mode matching the target refresh rate
        val curPx = display.mode.physicalWidth * display.mode.physicalHeight
        val targetMode = modes
            .filter { abs(it.refreshRate - targetRR) < 0.5f }
            .minByOrNull { abs(it.physicalWidth * it.physicalHeight - curPx) }

        if (targetMode != null && targetMode.modeId != display.mode.modeId) {
            applyDisplayModeId(targetMode.modeId)
        }

        // API 34+: set preferred refresh rate on window (works on LTPO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            applyPreferredRefreshRate(targetRR)
        }

        // Vivo OriginOS system-wide: vivo_screen_refresh_rate_mode in Settings.Global
        // This is the key the system Settings app uses for refresh rate switching
        val rateInt = targetRR.toInt()
        try {
            Settings.Global.putInt(contentResolver, "vivo_screen_refresh_rate_mode", rateInt)
            Log.d(TAG, "vivo_screen_refresh_rate_mode set to $rateInt")
        } catch (_: Exception) {}

        // Schedule a delayed refresh to catch the system applying the change
        handler.postDelayed({ refreshDisplayStates() }, 500L)
        isRefreshRateHigh.value = high
    }

    private fun applyPreferredRefreshRate(rate: Float) {
        try {
            val lp = window?.attributes ?: return
            lp.preferredRefreshRate = rate
            window?.attributes = lp
            Log.d(TAG, "preferredRefreshRate set to $rate")
        } catch (e: Exception) {
            Log.d(TAG, "preferredRefreshRate failed: ${e.message}")
        }
    }

    // ── Set Resolution ─────────────────────────────────────────────

    private fun setResolution(high: Boolean) {
        val display = defaultDisplay() ?: return
        val modes = display.supportedModes
        val resPx = modes.map { it.physicalWidth * it.physicalHeight }.distinct()

        if (resPx.size >= 2) {
            // Multiple resolution modes available — use direct mode switching
            val targetPx = if (high) resPx.max() else resPx.min()
            val curRR = display.mode.refreshRate
            val targetMode = modes
                .filter { it.physicalWidth * it.physicalHeight == targetPx }
                .minByOrNull { abs(it.refreshRate - curRR) }
            if (targetMode != null && targetMode.modeId != display.mode.modeId) {
                applyDisplayModeId(targetMode.modeId)
                Log.d(TAG, "setResolution high=$high → mode ${targetMode.modeId}")
            }
        } else {
            // Single native mode (2800×1260) — use IWindowManager to force display size
            // Vivo X300 Pro system settings: 超清=2800×1260, 高清=2400×1080
            val nativeW = 2800
            val nativeH = 1260

            if (high) {
                // 超清: clear forced size → revert to native 2800×1260
                clearForcedDisplaySize()
                clearForcedDisplayDensity()
                Log.d(TAG, "setResolution 超清 → clear forced size (revert to ${nativeW}x${nativeH})")
            } else {
                // 高清: force 2400×1080 with proportional density
                val lowW = 2400
                val lowH = 1080
                val dpiScale = lowW.toFloat() / nativeW.toFloat()
                val lowDensity = (resources.displayMetrics.densityDpi * dpiScale).toInt()

                val sizeOk = forceDisplaySize(lowW, lowH)
                val densityOk = forceDisplayDensity(lowDensity)
                Log.d(TAG, "setResolution 高清 → force ${lowW}x${lowH} density=$lowDensity sizeOk=$sizeOk densityOk=$densityOk")
            }
        }

        // Schedule a delayed refresh to catch the system applying the change
        handler.postDelayed({ refreshDisplayStates() }, 500L)
        isResolutionHigh.value = high
    }

    // ── IWindowManager Reflection ──────────────────────────────────

    @Suppress("PrivateApi", "DiscouragedPrivateApi")
    private fun getIWindowManager(): Any? {
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(null, Context.WINDOW_SERVICE) as? android.os.IBinder
                ?: return null
            val stub = Class.forName("android.view.IWindowManager\$Stub")
            val asInterface = stub.getDeclaredMethod("asInterface", android.os.IBinder::class.java)
            asInterface.invoke(null, binder)
        } catch (e: Exception) {
            Log.d(TAG, "getIWindowManager failed: ${e.message}")
            null
        }
    }

    private fun forceDisplaySize(width: Int, height: Int): Boolean {
        val wm = iwm ?: return false
        return try {
            val m = wm.javaClass.getDeclaredMethod(
                "setForcedDisplaySize",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!
            )
            m.invoke(wm, Display.DEFAULT_DISPLAY, width, height)
            true
        } catch (e: Exception) {
            Log.d(TAG, "setForcedDisplaySize failed: ${e.message}")
            false
        }
    }

    private fun clearForcedDisplaySize(): Boolean {
        val wm = iwm ?: return false
        return try {
            val m = wm.javaClass.getDeclaredMethod(
                "clearForcedDisplaySize",
                Int::class.javaPrimitiveType!!
            )
            m.invoke(wm, Display.DEFAULT_DISPLAY)
            true
        } catch (e: Exception) {
            Log.d(TAG, "clearForcedDisplaySize failed: ${e.message}")
            false
        }
    }

    private fun forceDisplayDensity(density: Int): Boolean {
        val wm = iwm ?: return false
        return try {
            val m = wm.javaClass.getDeclaredMethod(
                "setForcedDisplayDensityForUser",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!
            )
            m.invoke(wm, Display.DEFAULT_DISPLAY, density, 0)
            true
        } catch (e: Exception) {
            Log.d(TAG, "setForcedDisplayDensityForUser failed: ${e.message}")
            false
        }
    }

    private fun clearForcedDisplayDensity(): Boolean {
        val wm = iwm ?: return false
        return try {
            val m = wm.javaClass.getDeclaredMethod(
                "clearForcedDisplayDensityForUser",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!
            )
            m.invoke(wm, Display.DEFAULT_DISPLAY, 0)
            true
        } catch (e: Exception) {
            Log.d(TAG, "clearForcedDisplayDensityForUser failed: ${e.message}")
            false
        }
    }

    private fun applyDisplayModeId(modeId: Int) {
        val lp = window?.attributes ?: return
        lp.preferredDisplayModeId = modeId
        window?.attributes = lp
        Log.d(TAG, "preferredDisplayModeId set to $modeId")
    }

    // ── Display Listener ───────────────────────────────────────────

    private fun registerDisplayListener() {
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) {
                    Log.d(TAG, "onDisplayChanged — external change detected")
                    refreshDisplayStates()
                }
            }
        }
        displayManager.registerDisplayListener(displayListener, handler)
    }
}

// ────────────────────────────────────────────────────────────────────
//  UI (unchanged)
// ────────────────────────────────────────────────────────────────────

private val buttonColors = listOf(
    Color(0xFFFF8A80), Color(0xFFFFAB73), Color(0xFFFFC966), Color(0xFFAED16A),
    Color(0xFF72C87A), Color(0xFF60C0B0), Color(0xFF5BA8D0), Color(0xFF7088CC),
    Color(0xFF9078C0), Color(0xFFC078A8)
)

private val percentages = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)

@Composable
private fun MainScreen(
    audioManager: AudioManager,
    isRefreshRateHigh: Boolean,
    isResolutionHigh: Boolean,
    onRefreshRateToggle: (Boolean) -> Unit,
    onResolutionToggle: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            for (row in 0 until 5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    for (col in 0 until 2) {
                        val idx = row * 2 + col
                        VolumeButton(
                            modifier = Modifier.weight(1f),
                            percent = percentages[idx],
                            color = buttonColors[idx],
                            audioManager = audioManager
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ToggleButton(
                    modifier = Modifier.weight(1f),
                    label = "刷新率",
                    subLabel = if (isRefreshRateHigh) "高" else "低",
                    checked = isRefreshRateHigh,
                    color = Color(0xFF5C6BC0),
                    onCheckedChange = onRefreshRateToggle
                )
                ToggleButton(
                    modifier = Modifier.weight(1f),
                    label = "分辨率",
                    subLabel = if (isResolutionHigh) "高" else "低",
                    checked = isResolutionHigh,
                    color = Color(0xFF78909C),
                    onCheckedChange = onResolutionToggle
                )
            }
        }
    }
}

// ── Volume Button ──────────────────────────────────────────────────

@Composable
private fun VolumeButton(
    modifier: Modifier = Modifier,
    percent: Int,
    color: Color,
    audioManager: AudioManager
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f), label = "s"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 5.dp,
        animationSpec = spring(dampingRatio = 0.5f), label = "e"
    )
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation, shape).clip(shape).background(color)
            .clickable(interactionSource = interactionSource, indication = null) {
                applyVolume(audioManager, percent)
            }
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpeakerIcon(modifier = Modifier.size(24.dp), tint = Color.White, level = percent)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = "$percent%", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Toggle Button ──────────────────────────────────────────────────

@Composable
private fun ToggleButton(
    modifier: Modifier = Modifier,
    label: String,
    subLabel: String,
    checked: Boolean,
    color: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f), label = "ts"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 5.dp,
        animationSpec = spring(dampingRatio = 0.5f), label = "te"
    )
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation, shape).clip(shape).background(color)
            .clickable(interactionSource = interactionSource, indication = null) {
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(text = subLabel, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color.White.copy(alpha = 0.5f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.25f)
            )
        )
    }
}

// ── Speaker Icon ───────────────────────────────────────────────────

@Composable
private fun SpeakerIcon(modifier: Modifier = Modifier, tint: Color = Color.White, level: Int) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height; val sw = w * 0.08f
        drawRect(color = tint, topLeft = Offset(w * 0.15f, h * 0.32f), size = Size(w * 0.28f, h * 0.36f))
        val cone = Path().apply {
            moveTo(w * 0.43f, h * 0.32f)
            lineTo(w * 0.66f, h * 0.18f)
            lineTo(w * 0.66f, h * 0.82f)
            lineTo(w * 0.43f, h * 0.68f)
            close()
        }
        drawPath(cone, tint)
        if (level >= 40) drawArc(color = tint, startAngle = -22f, sweepAngle = 44f, useCenter = false,
            topLeft = Offset(w * 0.58f, h * 0.24f), size = Size(w * 0.30f, h * 0.52f),
            style = Stroke(width = sw, cap = StrokeCap.Round))
        if (level >= 70) drawArc(color = tint, startAngle = -22f, sweepAngle = 44f, useCenter = false,
            topLeft = Offset(w * 0.70f, h * 0.12f), size = Size(w * 0.30f, h * 0.76f),
            style = Stroke(width = sw, cap = StrokeCap.Round))
    }
}

// ── Volume Logic ───────────────────────────────────────────────────

private fun applyVolume(audioManager: AudioManager, percent: Int) {
    if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
        try { audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL) } catch (_: SecurityException) {}
    }
    for (stream in listOf(AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_ALARM)) {
        val max = audioManager.getStreamMaxVolume(stream)
        audioManager.setStreamVolume(stream, (max * percent / 100).coerceIn(0, max), 0)
    }
    audioManager.adjustSuggestedStreamVolume(AudioManager.ADJUST_SAME, AudioManager.STREAM_RING, AudioManager.FLAG_SHOW_UI)
}
