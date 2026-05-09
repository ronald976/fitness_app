package com.fitness.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitness.app.ui.navigation.Routes
import com.fitness.app.ui.theme.LocalFitnessColors

private data class TabSpec(
    val route: String,
    val label: String,
    val outlined: ImageVector,
    val filled: ImageVector
)

private val Tabs = listOf(
    TabSpec(Routes.Home, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    TabSpec(Routes.History, "History", Icons.Outlined.History, Icons.Filled.History),
    TabSpec(Routes.Profile, "Profile", Icons.Outlined.Person, Icons.Filled.Person)
)

@Composable
fun FitnessBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalFitnessColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface)
            .drawBehind {
                drawLine(
                    color = c.line,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tabs.forEach { tab ->
            val active = currentRoute == tab.route
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !active) { onNavigate(tab.route) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (active) tab.filled else tab.outlined,
                        contentDescription = tab.label,
                        tint = if (active) c.accent else c.fgDim,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = tab.label,
                        color = if (active) c.accent else c.fgDim,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
