package com.fitness.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitness.app.data.db.entities.UserEntity

@Composable
fun UserSwitcher(
    users: List<UserEntity>,
    currentUserId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (users.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        users.forEach { user ->
            FilterChip(
                selected = user.id == currentUserId,
                onClick = { onSelect(user.id) },
                label = { Text(user.name) }
            )
        }
    }
}
