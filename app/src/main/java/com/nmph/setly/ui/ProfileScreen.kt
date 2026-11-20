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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nmph.setly.ui.theme.AppCards
import com.nmph.setly.ui.theme.AppSpacing
import com.nmph.setly.ui.theme.IconBlue
import com.nmph.setly.ui.viewmodel.ProfileStats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    isSignedIn: Boolean,
    email: String?,
    stats: ProfileStats,
    lastSyncAt: Long?,
    isSyncing: Boolean,
    darkTheme: Boolean,
    notificationsEnabled: Boolean,
    autoRestEnabled: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleAutoRest: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val syncText = when {
        isSyncing -> "Синхронизация..."
        lastSyncAt != null -> {
            val time = Instant.ofEpochMilli(lastSyncAt)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
            "Последняя синхронизация: $time"
        }
        else -> "Ещё не синхронизировано"
    }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Профиль", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "На главный экран",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(AppSpacing.Block),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Block),
        ) {
            // Аккаунт
            item {
                if (isSignedIn) {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(IconBlue),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    initials(email),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                )
                            }
                            Column(
                                Modifier.padding(start = AppSpacing.Block).weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    "Вы в сети",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    email ?: "Аккаунт Google",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Button(
                            onClick = onSignOut,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) { Text("Выйти из аккаунта") }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppCards.Shape,
                        elevation = CardDefaults.cardElevation(defaultElevation = AppCards.Elevation),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.Block),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(IconBlue),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Гость",
                                    modifier = Modifier.size(36.dp),
                                    tint = Color.White,
                                )
                            }
                            Text(
                                "Гостевой режим",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "Данные хранятся только на этом устройстве. Войдите через Google, чтобы включить синхронизацию.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                            )
                            Button(
                                onClick = onSignIn,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            ) { Text("Войти через Google") }
                        }
                    }
                }
            }

            // Статистика
            item {
                Text("Статистика", style = MaterialTheme.typography.titleMedium)
                if (stats.completedWorkouts == 0 && stats.streakDays == 0 && stats.totalSets == 0) {
                    Text(
                        "Начните отмечать подходы — здесь появится ваша статистика.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
                ) {
                    StatCard(
                        icon = Icons.Default.CheckCircle,
                        label = "Выполнено тренировок",
                        value = stats.completedWorkouts.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.Default.DateRange,
                        label = "Текущая серия",
                        value = stats.streakDays.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.Default.FitnessCenter,
                        label = "Всего повторений",
                        value = stats.totalSets.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Настройки
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = IconBlue,
                    )
                    Text(
                        "Настройки",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = AppSpacing.CardInner),
                    )
                }
                SectionCard {
                    SettingRow("Темная тема", darkTheme, onToggleDarkTheme)
                    SettingRow("Уведомления", notificationsEnabled, onToggleNotifications)
                    SettingRow(
                        "Автоматический отдых",
                        autoRestEnabled,
                        onToggleAutoRest,
                    )
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
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            if (isSyncing) "Синхронизация..." else "Синхронизировать",
                            modifier = Modifier.padding(start = AppSpacing.CardInner),
                        )
                    }
                }
            }
        }
    }
}

private fun initials(email: String?): String {
    if (email.isNullOrBlank()) return "Г"
    val local = email.substringBefore('@')
        .replace(Regex("[._\\-]+"), " ")
        .trim()
    val parts = local.split(Regex("\\s+")).filter { it.isNotBlank() }
    return if (parts.size >= 2) {
        (parts[0].first().toString() + parts[1].first()).uppercase()
    } else {
        local.take(2).uppercase()
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCards.Shape,
        elevation = CardDefaults.cardElevation(defaultElevation = AppCards.Elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(AppSpacing.Block),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
            content = content,
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = AppCards.Shape,
        elevation = CardDefaults.cardElevation(defaultElevation = AppCards.Elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(AppSpacing.CardInner),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = IconBlue,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
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
