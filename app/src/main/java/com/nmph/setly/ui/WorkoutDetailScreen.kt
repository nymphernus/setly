package com.nmph.setly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmph.setly.data.local.WorkoutWithExercises
import com.nmph.setly.ui.components.EmptyStateView
import com.nmph.setly.ui.components.RestTimer
import com.nmph.setly.ui.components.SetPill
import com.nmph.setly.ui.theme.AppCards
import com.nmph.setly.ui.theme.AppSpacing
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
    onComplete: () -> Unit,
    autoRestEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val todayWorkouts by todayViewModel.todayWorkouts.collectAsStateWithLifecycle()
    val todayItem = todayWorkouts.firstOrNull { it.workout.id == workout?.workout?.id }

    var timeLeft by rememberSaveable { mutableStateOf(0) }
    var timerRun by rememberSaveable { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    val totalSets = todayItem?.totalSets ?: 0
    val completedSets = todayItem?.completedSets ?: 0
    val isDone = todayItem?.isDone == true
    val targetReps = todayItem?.exercises?.sumOf { it.targetForToday } ?: 0
    val doneReps = todayItem?.exercises?.sumOf { it.completedToday } ?: 0
    val remainingReps = (targetReps - doneReps).coerceAtLeast(0)
    val totalSeconds = if (workout == null) 0 else {
        DistributionHelper.effectiveRestMinutes(
            workout = workout.workout,
            totalSetsPerDay = totalSets,
            autoRestEnabled = autoRestEnabled,
        ) * 60
    }
    val restMinutes = if (workout == null) 0 else totalSeconds / 60

    LaunchedEffect(timerRun, totalSeconds) {
        if (timerRun == 0 || totalSeconds <= 0) return@LaunchedEffect
        // После поворота продолжаем с сохранённого остатка.
        var remaining = if (timeLeft > 0) timeLeft else totalSeconds
        timeLeft = remaining
        while (remaining > 0) {
            delay(1_000)
            remaining--
            timeLeft = remaining
        }
        snackbarHostState.showSnackbar("Отдых окончен! Пора делать следующий подход")
        timerRun = 0
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            workout?.workout?.name ?: "Тренировка",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        if (workout != null) {
                            Text(
                                "Период: ${workout.workout.periodDays} дней · Сон: ${workout.workout.sleepStart} - ${workout.workout.sleepEnd}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (timerRun > 0 && timeLeft > 0) {
                            Text(
                                "Отдых: %02d:%02d".format(timeLeft / 60, timeLeft % 60),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                },
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
        bottomBar = {
            if (workout != null) {
                Surface(tonalElevation = 2.dp) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.Block, vertical = AppSpacing.CardInner),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
                    ) {
                        Text(
                            "Выполнено $completedSets из $totalSets подходов. Осталось $remainingReps повторений",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onComplete,
                            enabled = isDone,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Text(
                                if (isDone) "Завершить тренировку"
                                else "Отметьте все подходы, чтобы завершить",
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        if (workout == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(AppSpacing.Block),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { Text("Тренировка не найдена") }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Block),
            contentPadding = PaddingValues(AppSpacing.Block),
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = AppCards.Shape,
                    elevation = CardDefaults.cardElevation(defaultElevation = AppCards.Elevation),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Text(
                        when {
                            workout.workout.autoRestTime && !autoRestEnabled ->
                                "Отдых: $restMinutes мин (авто выключено в профиле)"
                            workout.workout.autoRestTime -> "Отдых: $restMinutes мин (авто)"
                            else -> "Отдых: ${workout.workout.restTimeMinutes} мин"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(AppSpacing.CardInner),
                    )
                }
            }

            if (isDone) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppCards.Shape,
                        elevation = CardDefaults.cardElevation(defaultElevation = AppCards.Elevation),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.CardInner),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                "Все подходы выполнены — тренировка завершена",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = timerRun > 0 && timeLeft > 0 && totalSeconds > 0,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    RestTimer(timeLeft = timeLeft, totalSeconds = totalSeconds)
                }
            }

            when {
                workout.exercises.isEmpty() -> {
                    item {
                        EmptyStateView(
                            icon = Icons.Default.FitnessCenter,
                            title = "Добавьте упражнения в эту тренировку",
                            description = "Без упражнений нечего распределять по дням и отмечать в подходах.",
                            actionText = "Добавить упражнение",
                            onAction = onEdit,
                        )
                    }
                }

                todayItem == null -> {
                    item {
                        Text(
                            "Тренировка не активна сегодня — подходы появятся в её периоде.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(workout.exercises.size) { position ->
                        val exercise = workout.exercises[position]
                        val daily = DistributionHelper.dailyReps(workout.workout, exercise)
                        val sets = DistributionHelper.getSetsForExercise(exercise, daily)
                        Text(
                            "• ${exercise.name} — ${sets.joinToString(", ")} раз по дням",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                else -> {
                    items(todayItem.exercises.size) { position ->
                        val exercise = todayItem.exercises[position]
                        ExerciseSetsCard(
                            item = exercise,
                            onSetClick = { index, wasChecked ->
                                todayViewModel.toggleSet(exercise.exercise.id, index)
                                when {
                                    wasChecked -> {
                                        timerRun = 0
                                        timeLeft = 0
                                    }
                                    exercise.completedSetsCount + 1 >= exercise.sets.size -> {
                                        timerRun = 0
                                        timeLeft = 0
                                    }
                                    else -> {
                                        timeLeft = totalSeconds
                                        timerRun++
                                    }
                                }
                            },
                        )
                    }
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
    val backgroundColor by animateColorAsState(
        targetValue = if (item.isDone) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(250),
        label = "detailExerciseBackground",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = AppCards.Shape,
        elevation = CardDefaults.cardElevation(defaultElevation = AppCards.Elevation),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(AppSpacing.Block),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
        ) {
            Text(
                item.exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                "Сегодня: ${item.completedToday} повторений (${item.completedSetsCount} подходов)",
                style = MaterialTheme.typography.bodyMedium,
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
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
                ) {
                    item.sets.forEachIndexed { index, reps ->
                        val isChecked = index in item.completedIndexes
                        SetPill(
                            reps = reps,
                            checked = isChecked,
                            compact = true,
                            onClick = { onSetClick(index, isChecked) },
                        )
                    }
                }
            }
        }
    }
}
