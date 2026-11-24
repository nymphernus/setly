package com.nmph.setly.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmph.setly.ui.components.SetPill
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
            Text(
                "Нет активных тренировок на сегодня",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(workouts, key = { it.workout.id }) { workout ->
            TodayWorkoutCard(workout, viewModel)
        }
    }
}

@Composable
private fun TodayWorkoutCard(item: TodayWorkoutItem, viewModel: TodayViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.workout.name, style = MaterialTheme.typography.titleMedium)
            item.exercises.forEach { exercise ->
                TodayExerciseRow(
                    item = exercise,
                    onSetClick = { index -> viewModel.toggleSet(exercise.exercise.id, index) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayExerciseRow(
    item: TodayExerciseItem,
    onSetClick: (index: Int) -> Unit,
) {
    val done = item.isDone
    val rowColor by animateColorAsState(
        targetValue = if (done) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(250),
        label = "exerciseRowColor",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.exercise.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${item.completedToday} / ${item.dailyTargetReps}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (done) {
                Text(
                    "Выполнено",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item.sets.forEachIndexed { index, reps ->
                SetPill(
                    reps = reps,
                    checked = index in item.completedIndexes,
                    onClick = { onSetClick(index) },
                )
            }
        }
    }
}
