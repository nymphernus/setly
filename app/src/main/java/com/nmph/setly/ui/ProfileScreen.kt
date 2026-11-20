package com.nmph.setly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(
    isSignedIn: Boolean,
    email: String?,
    onSignIn: () -> Unit,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isSignedIn) "Синхронизировано с Google" else "Локальный режим",
            style = MaterialTheme.typography.headlineSmall,
        )
        if (isSignedIn) {
            Text(text = email.orEmpty())
            Button(onClick = onSync) { Text("Синхронизировать") }
            Button(onClick = onSignOut) { Text("Выйти") }
        } else {
            // Вход опционален: приложение уже работает в локальном режиме без аккаунта.
            Button(onClick = onSignIn) { Text("Войти через Google") }
        }
    }
}
