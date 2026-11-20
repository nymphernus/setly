package com.nmph.setly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.nmph.setly.data.local.WorkoutWithExercises
import com.nmph.setly.ui.components.EmptyStateView
import com.nmph.setly.ui.viewmodel.TodayWorkoutItem

@Composable
fun HomeScreen(
    workouts: List<WorkoutWithExercises>,
    todayWorkouts: List<TodayWorkoutItem> = emptyList(),
    onOpenWorkout: (Int) -> Unit,
    onEditWorkout: (Int) -> Unit,
    onDeleteWorkout: (WorkoutWithExercises) -> Unit,
    onCreateWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pendingDelete = remember { mutableStateOf<WorkoutWithExercises?>(null) }
    val progressByWorkout = remember(todayWorkouts) {
        todayWorkouts.associateBy({ it.workout.id }, { todayProgress(it) })
    }

    if (workouts.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.FitnessCenter,
            title = "У вас пока нет тренировок",
            description = "Создайте первую тренировку, чтобы план дней и прогресс появились здесь.",
            actionText = "Создать первую тренировку",
            onAction = onCreateWorkout,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(workouts, key = { it.workout.id }) { workout ->
                AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                    WorkoutCard(
                        item = workout,
                        progress = progressByWorkout[workout.workout.id] ?: 0f,
                        onOpen = { onOpenWorkout(workout.workout.id) },
                        onEdit = { onEditWorkout(workout.workout.id) },
                        onDelete = { pendingDelete.value = workout },
                    )
                }
            }
        }
    }

    pendingDelete.value?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete.value = null },
            title = { Text("Удалить тренировку?") },
            text = { Text("«${item.workout.name}» будет удалена с этого устройства.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteWorkout(item)
                    pendingDelete.value = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete.value = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun WorkoutCard(
    item: WorkoutWithExercises,
    progress: Float,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.workout.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Действия")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Редактировать") },
                            onClick = { menuExpanded = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить") },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
            Text(
                "Период: ${item.workout.periodDays} дней · Упражнений: ${item.exercises.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Сон: ${item.workout.sleepStart}–${item.workout.sleepEnd} · " +
                    "Отдых: ${restLabel(item)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Выполнено сегодня: ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

private fun todayProgress(item: TodayWorkoutItem): Float {
    if (item.exercises.isEmpty()) return 0f
    val done = item.exercises.count { exercise ->
        exercise.sets.isNotEmpty() && exercise.completedSets == exercise.sets
    }
    return done / item.exercises.size.toFloat()
}

private fun restLabel(item: WorkoutWithExercises): String = if (item.workout.autoRestTime) {
    "авто"
} else {
    "${item.workout.restTimeMinutes} мин"
}
