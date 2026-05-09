package com.fitness.app.ui.screens.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitness.app.LocalBottomBarPadding
import com.fitness.app.data.db.entities.UserEntity
import com.fitness.app.ui.theme.LocalFitnessColors

@Composable
fun ProfileScreen(
    onOpenStats: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val c = LocalFitnessColors.current
    var showSwitcher by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 16.dp)
            .padding(bottom = LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Profile",
            style = MaterialTheme.typography.displayLarge,
            color = c.fg,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.surface)
                .border(1.dp, c.line, RoundedCornerShape(18.dp))
                .clickable(enabled = state.users.size > 1) { showSwitcher = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                initials = (state.currentUser?.name ?: "?").take(2).uppercase(),
                bg = c.accent,
                fg = c.onAccent,
                size = 48
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.currentUser?.name ?: "Sign in",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.fg
                )
                if (state.users.size > 1) {
                    Text(
                        "Tap to switch · ${state.users.size} profiles",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.fgDim
                    )
                }
            }
        }

        ProfileRow(
            icon = Icons.Default.BarChart,
            label = "Stats",
            sub = "Volume, PRs, frequency, forecast",
            onClick = onOpenStats
        )
        ProfileRow(
            icon = Icons.Default.FormatListBulleted,
            label = "Plans",
            sub = "Manage workout templates",
            onClick = onOpenPlans
        )
        ProfileRow(
            icon = Icons.Default.Settings,
            label = "Settings",
            sub = "Rest chime, units, data import",
            onClick = onOpenSettings
        )
    }

    if (showSwitcher) {
        UserSwitcherDialog(
            users = state.users,
            currentUserId = state.currentUserId,
            onPick = { id ->
                viewModel.selectUser(id)
                showSwitcher = false
            },
            onDismiss = { showSwitcher = false }
        )
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    label: String,
    sub: String,
    onClick: () -> Unit
) {
    val c = LocalFitnessColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = c.fg, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleLarge, color = c.fg)
            Text(sub, style = MaterialTheme.typography.labelMedium, color = c.fgDim)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = c.fgDim
        )
    }
}

@Composable
internal fun Avatar(
    initials: String,
    bg: Color,
    fg: Color,
    size: Int
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            color = fg,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun UserSwitcherDialog(
    users: List<UserEntity>,
    currentUserId: Long?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalFitnessColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Switch profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                users.forEach { u ->
                    val active = u.id == currentUserId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) c.surface2 else c.surface)
                            .border(
                                1.dp,
                                if (active) c.accent else c.line,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onPick(u.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(
                            initials = u.name.take(2).uppercase(),
                            bg = if (active) c.accent else c.surface2,
                            fg = if (active) c.onAccent else c.fg,
                            size = 32
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(u.name, style = MaterialTheme.typography.titleMedium, color = c.fg)
                    }
                }
            }
        }
    )
}
