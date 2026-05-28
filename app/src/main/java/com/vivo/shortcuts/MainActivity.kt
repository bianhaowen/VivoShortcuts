package com.vivo.shortcuts

import android.media.AudioManager
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val w = (340 * resources.displayMetrics.density).toInt()
        window.setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.CENTER)

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        setContent {
            MainScreen(audioManager = audioManager)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) finish()
    }
}

// ── Color palette: smooth top-left → bottom-right ──────────────

private val buttonColors = listOf(
    Color(0xFFFF8A80), // 10%  coral
    Color(0xFFFFAB73), // 20%  orange
    Color(0xFFFFC966), // 30%  amber
    Color(0xFFAED16A), // 40%  lime
    Color(0xFF72C87A), // 50%  green
    Color(0xFF60C0B0), // 60%  teal
    Color(0xFF5BA8D0), // 70%  blue
    Color(0xFF7088CC), // 80%  indigo
    Color(0xFF9078C0), // 90%  purple
    Color(0xFFC078A8)  // 100% pink
)

private val percentages = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)

// ── Main Screen ─────────────────────────────────────────────────

@Composable
private fun MainScreen(audioManager: AudioManager) {
    Column(
        modifier = Modifier
            .width(340.dp)
            .background(Color(0xFFF5F5F5), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "音量快捷设置",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF888888),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        for (row in 0 until 5) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
            if (row < 4) Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// ── Volume Button ───────────────────────────────────────────────

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
        animationSpec = spring(dampingRatio = 0.5f),
        label = "press-scale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 5.dp,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "press-elevation"
    )

    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation, shape)
            .clip(shape)
            .background(color)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { applyVolume(audioManager, percent) }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpeakerIcon(
            modifier = Modifier.size(22.dp),
            tint = Color.White,
            level = percent
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$percent%",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Speaker Icon (Canvas) ───────────────────────────────────────

@Composable
private fun SpeakerIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    level: Int
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.08f

        drawRect(
            color = tint,
            topLeft = Offset(w * 0.15f, h * 0.32f),
            size = Size(w * 0.28f, h * 0.36f)
        )

        val cone = Path().apply {
            moveTo(w * 0.43f, h * 0.32f)
            lineTo(w * 0.66f, h * 0.18f)
            lineTo(w * 0.66f, h * 0.82f)
            lineTo(w * 0.43f, h * 0.68f)
            close()
        }
        drawPath(cone, tint)

        if (level >= 40) {
            drawArc(
                color = tint,
                startAngle = -22f,
                sweepAngle = 44f,
                useCenter = false,
                topLeft = Offset(w * 0.58f, h * 0.24f),
                size = Size(w * 0.30f, h * 0.52f),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
        if (level >= 70) {
            drawArc(
                color = tint,
                startAngle = -22f,
                sweepAngle = 44f,
                useCenter = false,
                topLeft = Offset(w * 0.70f, h * 0.12f),
                size = Size(w * 0.30f, h * 0.76f),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
    }
}

// ── Volume Logic ────────────────────────────────────────────────

private fun applyVolume(audioManager: AudioManager, percent: Int) {
    if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
        try {
            audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL)
        } catch (_: SecurityException) {
        }
    }

    val streams = listOf(
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_ALARM
    )

    for (stream in streams) {
        val max = audioManager.getStreamMaxVolume(stream)
        val target = (max * percent / 100).coerceIn(0, max)
        audioManager.setStreamVolume(stream, target, 0)
    }
}
