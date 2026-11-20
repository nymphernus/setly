package com.nmph.setly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmph.setly.ui.components.RestTimer
import com.nmph.setly.ui.components.SetPill
import com.nmph.setly.ui.viewmodel.TodayExerciseItem
import com.nmph.setly.ui.viewmodel.TodayViewModel
import com.nmph.setly.ui.viewmodel.TodayWorkoutItem
import com.nmph.setly.utils.DistributionHelper
import kotlinx.coroutines.delay

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
    var timeLeft by remember { mutableStateOf(0) }
    var timerRun by remember { mutableStateOf(0) }
    val totalSets = item.exercises.sumOf { it.sets.size }
    val totalSeconds = DistributionHelper.effectiveRestMinutes(item.workout, totalSets) * 60

    LaunchedEffect(timerRun) {
        if (timerRun == 0 || totalSeconds <= 0) return@LaunchedEffect
        var remaining = totalSeconds
        timeLeft = remaining
        while (remaining > 0) {
            delay(1_000)
            remaining--
            timeLeft = remaining
        }
    }

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

            AnimatedVisibility(
                visible = timerRun > 0 && totalSeconds > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                RestTimer(timeLeft = timeLeft, totalSeconds = totalSeconds)
            }

            item.exercises.forEach { exercise ->
                TodayExerciseRow(
                    item = exercise,
                    viewModel = viewModel,
                    onSetChecked = { wasChecked ->
                        if (wasChecked) {
                            timerRun = 0
                            timeLeft = 0
                        } else {
                            timerRun++
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayExerciseRow(
    item: TodayExerciseItem,
    viewModel: TodayViewModel,
    onSetChecked: (wasChecked: Boolean) -> Unit,
) {
    val done = item.sets.isNotEmpty() && item.completedSets == item.sets
    val rowColor by animateColorAsState(
        targetValue = if (done) MaterialTheme.colorScheme.primaryContainer
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
                "${item.completedToday} / ${item.targetForToday}",
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
                    checked = item.completedSets.getOrNull(index) == reps,
                    onClick = {
                        val wasChecked = item.completedSets.getOrNull(index) == reps
                        viewModel.toggleSet(item.exercise.id, index)
                        onSetChecked(wasChecked)
                    },
                )
            }
        }
    }
}
