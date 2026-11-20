package com.nmph.setly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nmph.setly.ui.viewmodel.ProfileStats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ProfileScreen(
    isSignedIn: Boolean,
    email: String?,
    stats: ProfileStats,
    lastSyncAt: Long?,
    isSyncing: Boolean,
    darkTheme: Boolean,
    notificationsEnabled: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val syncText = when {
        isSyncing -> "Синхронизация..."
        lastSyncAt != null -> {
            val time = Instant.ofEpochMilli(lastSyncAt)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            "Последняя синхронизация: $time"
        }
        else -> "Ещё не синхронизировано"
    }
    val initials = email?.takeIf { it.isNotBlank() }?.first()?.uppercase() ?: "S"

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Профиль", style = MaterialTheme.typography.headlineMedium)
        }

        // Аккаунт
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initials,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Column(
                        Modifier.padding(start = 16.dp).weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (isSignedIn) "Синхронизировано с Google" else "Локальный режим",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            email ?: "Войдите, чтобы включить синхронизацию",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = if (isSignedIn) onSignOut else onSignIn,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Text(if (isSignedIn) "Выйти" else "Войти через Google")
                }
            }
        }

        // Статистика
        item {
            Text("Статистика", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Тренировок", stats.completedWorkouts.toString(), Modifier.weight(1f))
                StatCard("Серия, дней", stats.streakDays.toString(), Modifier.weight(1f))
                StatCard("Повторений", stats.totalReps.toString(), Modifier.weight(1f))
            }
        }

        // Настройки
        item {
            Text("Настройки", style = MaterialTheme.typography.titleMedium)
            SectionCard {
                SettingRow("Тёмная тема", darkTheme, onToggleDarkTheme)
                SettingRow("Уведомления", notificationsEnabled, onToggleNotifications)
            }
        }

        // Синхронизация
        item {
            Text("Синхронизация", style = MaterialTheme.typography.titleMedium)
            SectionCard {
                Text(syncText, style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = onSync,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("Синхронизировать сейчас") }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
