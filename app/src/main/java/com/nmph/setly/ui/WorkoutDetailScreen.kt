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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.nmph.setly.data.local.WorkoutWithExercises
import com.nmph.setly.ui.components.RestTimer
import com.nmph.setly.ui.components.SetPill
import com.nmph.setly.ui.viewmodel.TodayExerciseItem
import com.nmph.setly.ui.viewmodel.TodayViewModel
import com.nmph.setly.utils.DistributionHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    workout: WorkoutWithExercises?,
    todayViewModel: TodayViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val todayWorkouts by todayViewModel.todayWorkouts.collectAsStateWithLifecycle()
    val todayItem = todayWorkouts.firstOrNull { it.workout.id == workout?.workout?.id }

    var timeLeft by remember { mutableStateOf(0) }
    var timerRun by remember { mutableStateOf(0) }
    val totalSets = todayItem?.exercises?.sumOf { it.sets.size } ?: 0
    val totalSeconds = if (workout == null) 0 else {
        DistributionHelper.effectiveRestMinutes(workout.workout, totalSets) * 60
    }

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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Тренировка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (workout == null) {
            Column(
                Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { Text("Тренировка не найдена") }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(workout.workout.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Период: ${workout.workout.periodDays} дней · Упражнений: ${workout.exercises.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Сон: ${workout.workout.sleepStart}–${workout.workout.sleepEnd}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (workout.workout.autoRestTime) {
                                "Отдых: авто ($totalSets подходов → ${totalSeconds / 60} мин)"
                            } else {
                                "Отдых: ${workout.workout.restTimeMinutes} мин"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = timerRun > 0 && totalSeconds > 0,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    RestTimer(timeLeft = timeLeft, totalSeconds = totalSeconds)
                }
            }

            if (todayItem == null) {
                item {
                    Text(
                        "Тренировка не активна сегодня — подходы появятся в её период.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(todayItem.exercises.size) { position ->
                    val exercise = todayItem.exercises[position]
                    ExerciseSetsCard(
                        item = exercise,
                        onSetClick = { index, wasChecked ->
                            todayViewModel.toggleSet(exercise.exercise.id, index)
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

            if (todayItem == null) {
                items(workout.exercises.size) { position ->
                    val exercise = workout.exercises[position]
                    Text(
                        "• ${exercise.name} — ${exercise.targetReps} раз (${exercise.repsPerSet} в подходе)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseSetsCard(
    item: TodayExerciseItem,
    onSetClick: (index: Int, wasChecked: Boolean) -> Unit,
) {
    val done = item.sets.isNotEmpty() && item.completedSets == item.sets
    val backgroundColor by animateColorAsState(
        targetValue = if (done) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(250),
        label = "detailExerciseBackground",
    )
    Card(
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
            }
            Text(
                "По ${item.exercise.repsPerSet} повторений в подходе",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.sets.isEmpty()) {
                Text(
                    "Нет подходов на сегодня",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
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
                                onSetClick(index, wasChecked)
                            },
                        )
                    }
                }
            }
        }
    }
}
