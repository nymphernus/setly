package com.nmph.setly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.ui.components.EmptyStateView
import com.nmph.setly.ui.viewmodel.ExerciseViewModel

/** Глобальный список упражнений: создания/редактирования/удаления. */
@Composable
fun ExerciseScreen(
    viewModel: ExerciseViewModel,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    var pendingDeleteId by rememberSaveable { mutableStateOf<Int?>(null) }
    val pendingDelete = exercises.firstOrNull { it.id == pendingDeleteId }

    if (exercises.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.FitnessCenter,
            title = "Список упражнений пуст",
            description = "Добавьте упражнения один раз, затем используйте их в любых тренировках.",
            actionText = "Добавить упражнение",
            onAction = onAdd,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(exercises, key = { it.id }) { exercise ->
            AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                ExerciseCard(
                    exercise = exercise,
                    onEdit = { onEdit(exercise.id) },
                    onDelete = { pendingDeleteId = exercise.id },
                )
            }
        }
    }

    pendingDelete?.let { exercise ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Удалить упражнение?") },
            text = { Text("«${exercise.name}» будет удалено из списка и из всех тренировок.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExercise(exercise)
                    pendingDeleteId = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "В одном подходе: ${exercise.repsPerSet} раз",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Цель на период задаётся в тренировке",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Редактировать упражнение")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить упражнение")
            }
        }
    }
}
