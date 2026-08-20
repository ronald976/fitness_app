package com.fitness.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitness.app.ui.theme.LocalFitnessColors

/** How long the user has to leave the screen alone before it dims down to just the clock. */
const val REST_FOCUS_IDLE_MS = 6_000L

/**
 * Full-bleed "rack view" for the rest period: everything fades to black except a big orange
 * countdown, readable at arm's length from a bench. Any tap brings the workout back; it also
 * clears itself when the rest runs out.
 */
@Composable
fun RestFocusOverlay(
    totalSeconds: Int,
    startedAtMs: Long,
    restKey: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalFitnessColors.current
    val remainingMs = rememberRestRemainingMs(totalSeconds, startedAtMs, restKey)
    val secondsLeft = (remainingMs / 1000).toInt()

    // Fade in rather than cutting to black, so it reads as the screen settling down.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "restFocusFade"
    )

    val label = if (secondsLeft >= 60) {
        "%d:%02d".format(secondsLeft / 60, secondsLeft % 60)
    } else {
        secondsLeft.toString()
    }

    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(Color.Black)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                color = c.accent,
                fontWeight = FontWeight.ExtraBold,
                // "2:05" needs to be smaller than a bare "45" to stay on one line.
                fontSize = if (label.length <= 2) 160.sp else 110.sp,
                letterSpacing = (-4).sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Tap to resume",
                color = Color.White.copy(alpha = 0.35f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}
