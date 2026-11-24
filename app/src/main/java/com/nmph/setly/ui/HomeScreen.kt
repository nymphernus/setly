package com.nmph.setly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nmph.setly.data.local.WorkoutPlan
import com.nmph.setly.ui.components.EmptyStateView
import com.nmph.setly.ui.theme.AppCards
import com.nmph.setly.ui.theme.AppSpacing
import com.nmph.setly.ui.viewmodel.TodayWorkoutItem
import com.nmph.setly.utils.DistributionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    workouts: List<WorkoutPlan>,
    todayWorkouts: List<TodayWorkoutItem> = emptyList(),
    onOpenWorkout: (Int) -> Unit,
    onEditWorkout: (Int) -> Unit,
    onDeleteWorkout: (WorkoutPlan) -> Unit,
    onCreateWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pendingDelete = remember { mutableStateOf<WorkoutPlan?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val progressByWorkout = remember(todayWorkouts) {
        todayWorkouts.associateBy({ it.workout.id }, { it })
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Мои тренировки",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                },
            )
        },
    ) { innerPadding ->
        if (workouts.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.FitnessCenter,
                title = "У вас пока нет тренировок",
                description = "Создайте первую тренировку, чтобы план дней и прогресс появились здесь.",
                actionText = "Создать тренировку",
                onAction = onCreateWorkout,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Block),
                contentPadding = PaddingValues(AppSpacing.Block),
            ) {
                items(workouts, key = { it.workout.id }) { workout ->
                    AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                        WorkoutCard(
                            item = workout,
                            todayItem = progressByWorkout[workout.workout.id],
                            onOpen = { onOpenWorkout(workout.workout.id) },
                            onEdit = { onEditWorkout(workout.workout.id) },
                            onDelete = { pendingDelete.value = workout },
                        )
                    }
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
    item: WorkoutPlan,
    todayItem: TodayWorkoutItem?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val completed = todayItem?.completedSets ?: 0
    val total = todayItem?.totalSets ?: 0
    val progress = if (total == 0) 0f else completed / total.toFloat()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onOpen),
        shape = AppCards.Shape,
        elevation = CardDefaults.cardElevation(defaultElevation = AppCards.Elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(AppSpacing.Block),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
        ) {
            BoxWithConstraints {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.workout.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            "Период: ${item.workout.periodDays} дней · Упражнений: ${item.exercises.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (this@BoxWithConstraints.maxWidth < 360.dp) {
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
                    } else {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Редактировать тренировку")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Удалить тренировку",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            val totalSets = todayItem?.totalSets
                ?: item.exercises.sumOf {
                    DistributionHelper.setsForPlan(it, item.workout).size
                }
            Text(
                "Отдых: ${DistributionHelper.restMinutes(item.workout.sleepStart, item.workout.sleepEnd, totalSets)} мин",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Выполнено $completed из $total подходов сегодня",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}
