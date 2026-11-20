package com.nmph.setly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    isSignedIn: Boolean,
    email: String?,
    lastSyncAt: Long?,
    isSyncing: Boolean,
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
                .toLocalTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            "Последняя синхронизация: $time"
        }
        else -> "Ещё не синхронизировано"
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isSignedIn) {
                    "Синхронизировано с Google (${email.orEmpty()})"
                } else {
                    "Локальный режим (войдите для синхронизации)"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (isSignedIn) {
                Text(syncText, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onSync, enabled = !isSyncing) {
                    Text("Синхронизировать сейчас")
                }
                Button(onClick = onSignOut) { Text("Выйти") }
            } else {
                // Вход опционален: приложение работает в локальном режиме без аккаунта.
                Button(onClick = onSignIn) { Text("Войти через Google") }
            }
        }
    }
}
