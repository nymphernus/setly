package com.nmph.setly.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    // Экран упражнений появится на следующем этапе; сейчас это стартовая точка offline-first.
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Список упражнений (в разработке)")
    }
}
