package com.fitness.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitness.app.ui.theme.LocalFitnessColors
import kotlinx.coroutines.delay

@Composable
fun RestTimer(
    totalSeconds: Int,
    restKey: Int = 0,
    onDismiss: () -> Unit,
    onSetRemaining: (newTotalSec: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalFitnessColors.current
    var remainingMs by remember(totalSeconds, restKey) { mutableLongStateOf(totalSeconds * 1000L) }

    LaunchedEffect(totalSeconds, restKey) {
        val endAt = System.currentTimeMillis() + totalSeconds * 1000L
        while (true) {
            val left = endAt - System.currentTimeMillis()
            remainingMs = left.coerceAtLeast(0)
            if (left <= 0) break
            delay(200)
        }
    }

    val secondsLeft = (remainingMs / 1000).toInt()
    val totalLabel = formatTime(totalSeconds)
    val leftLabel = formatTime(secondsLeft)
    val progress = (remainingMs.toFloat() / (totalSeconds * 1000f)).coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.fg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ring
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(40.dp)) {
                val stroke = 4.dp.toPx()
                val pad = stroke / 2
                drawArc(
                    color = c.bg.copy(alpha = 0.18f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = c.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Resting",
                style = MaterialTheme.typography.labelSmall,
                color = c.bg.copy(alpha = 0.65f),
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    leftLabel,
                    color = c.bg,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFeatureSettings = "tnum",
                        letterSpacing = (-0.3).sp,
                        fontSize = 18.sp
                    )
                )
                Text(
                    " / $totalLabel",
                    color = c.bg.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFeatureSettings = "tnum"
                    )
                )
            }
        }
        TimerPill(
            label = "+30s",
            bg = c.bg.copy(alpha = 0.12f),
            fg = c.bg,
            onClick = { onSetRemaining((secondsLeft + 30).coerceIn(5, 600)) }
        )
        Spacer(Modifier.width(6.dp))
        TimerPill(
            label = "Skip",
            bg = c.accent,
            fg = c.onAccent,
            onClick = onDismiss
        )
    }
}

@Composable
private fun TimerPill(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = fg,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatTime(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
