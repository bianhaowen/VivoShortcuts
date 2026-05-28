package com.vivo.shortcuts

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivo.shortcuts.ui.theme.VivoShortcutsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = 20
            }
        }

        setContent {
            VivoShortcutsTheme {
                VolumeFloatingWindow(onDismiss = { finishAndRemoveTask() })
            }
        }
    }
}

private data class VolButton(
    val percent: Int,
    val startColor: Color,
    val endColor: Color
)

private val volumeButtons = listOf(
    VolButton(10, Color(0xFFFF8A80), Color(0xFFFFB4AB)),
    VolButton(20, Color(0xFFFFAB73), Color(0xFFFFC9A3)),
    VolButton(30, Color(0xFFFFC966), Color(0xFFFFDA91)),
    VolButton(40, Color(0xFFAED16A), Color(0xFFCBDF8E)),
    VolButton(50, Color(0xFF72C87A), Color(0xFF98D89F)),
    VolButton(60, Color(0xFF60C0B0), Color(0xFF86D4C8)),
    VolButton(70, Color(0xFF5BA8D0), Color(0xFF82C2E0)),
    VolButton(80, Color(0xFF7088CC), Color(0xFF96A8DC)),
    VolButton(90, Color(0xFF9078C0), Color(0xFFAE9CD4)),
    VolButton(100, Color(0xFFC078A8), Color(0xFFD49CC0))
)

@Composable
fun VolumeFloatingWindow(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.88f))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.85f),
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.60f)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume click */ }
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "音量快捷设置",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF777777),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                volumeButtons.take(5).forEach { btn ->
                    VolButtonView(btn, audioManager)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                volumeButtons.drop(5).forEach { btn ->
                    VolButtonView(btn, audioManager)
                }
            }
        }
    }
}

@Composable
private fun VolButtonView(
    btn: VolButton,
    audioManager: AudioManager
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(btn.startColor, btn.endColor)
                )
            )
            .clickable {
                applyVolume(audioManager, btn.percent)
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SpeakerIcon(
                modifier = Modifier.size(20.dp),
                tint = Color.White,
                level = btn.percent
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = "${btn.percent}%",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )
        }
    }
}

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
