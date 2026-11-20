package com.nmph.setly.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nmph.setly.data.local.WorkoutWithExercises

@Composable
fun HomeScreen(
    workouts: List<WorkoutWithExercises>,
    onOpenWorkout: (Int) -> Unit,
    onEditWorkout: (Int) -> Unit,
    onDeleteWorkout: (WorkoutWithExercises) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pendingDelete = remember { mutableStateOf<WorkoutWithExercises?>(null) }

    if (workouts.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Пока нет тренировок",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Нажмите «+», чтобы создать первую",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(workouts, key = { it.workout.id }) { workout ->
                WorkoutCard(
                    item = workout,
                    onOpen = { onOpenWorkout(workout.workout.id) },
                    onEdit = { onEditWorkout(workout.workout.id) },
                    onDelete = { pendingDelete.value = workout },
                )
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
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        }
    }
}

private fun restLabel(item: WorkoutWithExercises): String = if (item.workout.autoRestTime) {
    "авто"
} else {
    "${item.workout.restTimeMinutes} мин"
}
