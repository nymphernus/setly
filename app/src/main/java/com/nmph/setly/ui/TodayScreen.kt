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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmph.setly.ui.components.PhaseTimer
import com.nmph.setly.ui.components.SetPill
import com.nmph.setly.ui.viewmodel.TimerPhase
import com.nmph.setly.ui.viewmodel.TodayExerciseItem
import com.nmph.setly.ui.viewmodel.TodayViewModel
import com.nmph.setly.ui.viewmodel.TodayWorkoutItem
import com.nmph.setly.utils.DistributionHelper

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
    val timerState by viewModel.timer.collectAsStateWithLifecycle()
    var showCancelDialog by remember { mutableStateOf(false) }
    var pendingToggle by remember { mutableStateOf<Pair<() -> Unit, () -> Unit>?>(null) }
    val restMinutes = DistributionHelper.restMinutes(
        item.workout.sleepStart,
        item.workout.sleepEnd,
        item.totalSets,
    )

    fun onToggleRequested(
        exerciseId: Int,
        reps: Int,
        unit: String,
        apply: () -> Unit,
        startTimer: Boolean,
    ) {
        val start: () -> Unit = if (startTimer) {
            { viewModel.startSetTimer(exerciseId, reps, unit, restMinutes) }
        } else {
            {}
        }
        if (timerState.isActive) {
            pendingToggle = apply to start
            showCancelDialog = true
        } else {
            apply()
            start()
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

            if (timerState.isActive) {
                Text(
                    "${if (timerState.phase == TimerPhase.EXECUTION) "Выполнение" else "Отдых"}: " +
                        "%02d:%02d".format(timerState.timeLeft / 60, timerState.timeLeft % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (timerState.phase == TimerPhase.EXECUTION) {
                        Color(0xFF2196F3)
                    } else {
                        Color(0xFFFF9800)
                    },
                )
            }

            AnimatedVisibility(
                visible = timerState.isActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                PhaseTimer(state = timerState)
            }

            item.exercises.forEach { exercise ->
                TodayExerciseRow(
                    item = exercise,
                    viewModel = viewModel,
                    onSetClick = { index, wasChecked ->
                        onToggleRequested(
                            exerciseId = exercise.exercise.id,
                            reps = exercise.sets.getOrNull(index) ?: exercise.repsPerSet,
                            unit = exercise.exercise.unit,
                            apply = { viewModel.toggleSet(exercise.exercise.id, index) },
                            startTimer = !wasChecked,
                        )
                    },
                )
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Отменить подход?") },
            text = { Text("Таймер отдыха будет сброшен.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelTimer()
                        pendingToggle?.first?.invoke()
                        pendingToggle?.second?.invoke()
                        pendingToggle = null
                        showCancelDialog = false
                    },
                ) { Text("Да, отменить") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Нет, продолжить") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayExerciseRow(
    item: TodayExerciseItem,
    viewModel: TodayViewModel,
    onSetClick: (index: Int, wasChecked: Boolean) -> Unit,
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
                val isChecked = index in item.completedIndexes
                SetPill(
                    reps = reps,
                    checked = isChecked,
                    onClick = { onSetClick(index, isChecked) },
                )
            }
        }
    }
}
