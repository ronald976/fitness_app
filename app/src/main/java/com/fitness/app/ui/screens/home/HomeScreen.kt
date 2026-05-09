package com.fitness.app.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitness.app.LocalBottomBarPadding
import com.fitness.app.ui.screens.profile.Avatar
import com.fitness.app.ui.theme.LocalFitnessColors
import com.fitness.app.ui.theme.TilePalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun HomeScreen(
    onStartWorkout: (Long) -> Unit,
    onBrowsePlans: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val c = LocalFitnessColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 6.dp)
            .padding(bottom = 16.dp + LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HomeHeader(
            userName = state.currentUser?.name,
            initials = (state.currentUser?.name ?: "?").take(2).uppercase()
        )

        if (state.activePlan == null) {
            NoActivePlanCard(onBrowsePlans = onBrowsePlans)
        } else {
            val today = state.todayDay
            val todayMeta = today?.day?.id?.let { state.dayMeta[it] }
            if (today != null && todayMeta != null) {
                TodayHeroCard(
                    planName = state.activePlan!!.plan.name,
                    dayName = today.day.name,
                    meta = todayMeta,
                    cycleIndex = state.cycleIndex,
                    cycleSize = state.cycleSize,
                    onStart = { viewModel.startDay(today.day.id, onStartWorkout) }
                )
            }

            // "Or pick another"
            if (state.otherDayIds.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            "Or pick another",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.fg,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${state.activePlan!!.plan.name} · ${state.cycleSize} days",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.fgDim
                        )
                    }
                    state.otherDayIds.forEach { id ->
                        val meta = state.dayMeta[id] ?: return@forEach
                        PlanDayRow(
                            meta = meta,
                            onStart = { viewModel.startDay(id, onStartWorkout) }
                        )
                    }
                }
            }
        }

        // Custom workout — always available
        CustomWorkoutRow(onStart = { viewModel.startCustomWorkout(onStartWorkout) })
    }
}

@Composable
private fun HomeHeader(userName: String?, initials: String) {
    val c = LocalFitnessColors.current
    val weekday = remember { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(initials = initials, bg = c.accent, fg = c.onAccent, size = 36)
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(weekday, style = MaterialTheme.typography.labelMedium, color = c.fgDim)
            Text(
                if (userName != null) "Hi, $userName" else "Welcome",
                style = MaterialTheme.typography.titleLarge,
                color = c.fg
            )
        }
    }
}

@Composable
private fun TodayHeroCard(
    planName: String,
    dayName: String,
    meta: DayMeta,
    cycleIndex: Int,
    cycleSize: Int,
    onStart: () -> Unit
) {
    val c = LocalFitnessColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = c.accent,
                spotColor = c.accent
            )
            .clip(RoundedCornerShape(22.dp))
            .background(c.accent)
    ) {
        // Decorative concentric rings, top-right corner
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(180.dp)
                .padding(start = 0.dp)
        ) {
            val cx = size.width * 0.7f
            val cy = -size.height * 0.2f
            val white = Color.White.copy(alpha = 0.18f)
            drawCircle(white, radius = size.minDimension * 0.5f, center = androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(c.accent, radius = size.minDimension * 0.33f, center = androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(white, radius = size.minDimension * 0.18f, center = androidx.compose.ui.geometry.Offset(cx, cy))
        }
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "TODAY'S WORKOUT",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 0.5.sp
                ),
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                dayName,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 30.sp,
                    letterSpacing = (-0.9).sp
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "$planName · ${meta.exerciseCount} exercises · ${meta.totalSets} sets · ~${meta.estMinutes} min",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )

            // Stats row
            if (meta.lastDurationLabel != null || meta.lastVolumeLabel != null || cycleSize > 0) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (meta.lastDurationLabel != null) {
                        StatBlock(label = "Last time", value = meta.lastDurationLabel)
                    }
                    if (meta.lastVolumeLabel != null) {
                        StatBlock(label = "Volume", value = meta.lastVolumeLabel)
                    }
                    if (cycleSize > 0) {
                        StatBlock(label = "Day", value = "$cycleIndex / $cycleSize")
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable(onClick = onStart)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Start workout",
                    color = c.accent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun PlanDayRow(meta: DayMeta, onStart: () -> Unit) {
    val c = LocalFitnessColors.current
    val tile = TilePalette[abs(meta.name.hashCode()) % TilePalette.size]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onStart)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tile.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = tile, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(meta.name, style = MaterialTheme.typography.titleMedium, color = c.fg)
            Text(
                "${meta.exerciseCount} ex · ${meta.totalSets} sets · ~${meta.estMinutes} min",
                style = MaterialTheme.typography.labelMedium,
                color = c.fgDim
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(17.dp))
                .background(c.fg)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = c.bg,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Start",
                    color = c.bg,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CustomWorkoutRow(onStart: () -> Unit) {
    val c = LocalFitnessColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onStart)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = c.fg, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Custom workout", style = MaterialTheme.typography.titleMedium, color = c.fg)
            Text(
                "Empty session — pick exercises as you go",
                style = MaterialTheme.typography.labelMedium,
                color = c.fgDim
            )
        }
    }
}

@Composable
private fun NoActivePlanCard(onBrowsePlans: () -> Unit) {
    val c = LocalFitnessColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("No active plan", style = MaterialTheme.typography.titleLarge, color = c.fg)
        Text(
            "Pick a plan to see your day's workout here.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.fgDim
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(c.accent)
                .clickable(onClick = onBrowsePlans)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                "Browse plans",
                color = c.onAccent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

