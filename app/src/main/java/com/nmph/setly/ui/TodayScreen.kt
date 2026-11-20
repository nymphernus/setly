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
import com.nmph.setly.ui.viewmodel.TodayExerciseItem
import com.nmph.setly.ui.viewmodel.TodayViewModel
import com.nmph.setly.ui.viewmodel.TodayWorkoutItem

@Composable
fun TodayScreen(viewModel: TodayViewModel, modifier: Modifier = Modifier) {
    val workouts by viewModel.todayWorkouts.collectAsStateWithLifecycle()

    if (workouts.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Нет активных тренировок на сегодня", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        items(workouts, key = { it.workout.id }) { workout ->
            TodayWorkoutCard(workout, viewModel)
        }
    }
}

@Composable
private fun TodayWorkoutCard(item: TodayWorkoutItem, viewModel: TodayViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.workout.name, style = MaterialTheme.typography.titleMedium)
            item.exercises.forEach { exercise ->
                TodayExerciseRow(exercise, viewModel)
            }
        }
    }
}

@Composable
private fun TodayExerciseRow(item: TodayExerciseItem, viewModel: TodayViewModel) {
    val done = item.completedToday >= item.targetForToday
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (done) Modifier.background(Color(0xFFC8E6C9)) else Modifier)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(item.exercise.name, modifier = Modifier.weight(1f))
        Button(onClick = { viewModel.updateProgress(item.exercise.id, item.completedToday - 1) }) {
            Text("-1")
        }
        Text("${item.completedToday} / ${item.targetForToday}")
        Button(onClick = { viewModel.updateProgress(item.exercise.id, item.completedToday + 1) }) {
            Text("+1")
        }
    }
}
