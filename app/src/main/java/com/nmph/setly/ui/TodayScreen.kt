package com.nmph.setly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmph.setly.ui.viewmodel.TodayItem
import com.nmph.setly.ui.viewmodel.TodayViewModel

@Composable
fun TodayScreen(viewModel: TodayViewModel, modifier: Modifier = Modifier) {
    val items by viewModel.todayItems.collectAsStateWithLifecycle()

    if (items.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Нет упражнений на сегодня", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        items(items, key = { it.exercise.id }) { item ->
            TodayCard(item, viewModel)
        }
    }
}

@Composable
private fun TodayCard(item: TodayItem, viewModel: TodayViewModel) {
    val done = item.completedToday >= item.targetForToday
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (done) Modifier.background(Color(0xFFC8E6C9)) else Modifier),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(item.exercise.name, style = MaterialTheme.typography.titleMedium)
            Text("Цель на сегодня: ${item.targetForToday} раз")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { viewModel.updateProgress(item.exercise.id, item.completedToday - 1) }) {
                    Text("-1")
                }
                Text("${item.completedToday} / ${item.targetForToday}")
                Button(onClick = { viewModel.updateProgress(item.exercise.id, item.completedToday + 1) }) {
                    Text("+1")
                }
                if (done) {
                    Text("Выполнено", color = Color(0xFF2E7D32), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
