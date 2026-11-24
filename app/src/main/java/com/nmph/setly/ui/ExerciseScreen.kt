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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.ui.components.EmptyStateView
import com.nmph.setly.ui.theme.AppCards
import com.nmph.setly.ui.theme.AppSpacing
import com.nmph.setly.ui.viewmodel.ExerciseViewModel

/** Глобальный список упражнений: создание/редактирование/удаление. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(
    viewModel: ExerciseViewModel,
    onEdit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeleteId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    val pendingDelete = exercises.firstOrNull { it.id == pendingDeleteId }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Упражнения",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить упражнение")
            }
        },
    ) { innerPadding ->
        if (exercises.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.FitnessCenter,
                title = "Список упражнений пуст",
                description = "Добавьте упражнения один раз, затем используйте их в любых тренировках.",
                actionText = "Добавить упражнение",
                onAction = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
                contentPadding = PaddingValues(AppSpacing.Block),
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
        }
    }

    if (showCreateDialog) {
        CreateExerciseDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description ->
                viewModel.addExercise(name, description)
                showCreateDialog = false
            },
        )
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
private fun CreateExerciseDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое упражнение") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    placeholder = { Text("Например, Отжимания") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание (необязательно)") },
                    placeholder = { Text("Например, грудь и трицепс") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim(), description.trim()) },
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCards.Shape,
        elevation = CardDefaults.cardElevation(defaultElevation = AppCards.Elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.CardInner),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    exercise.description.ifBlank { "Описание не добавлено" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Редактировать упражнение")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить упражнение",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
