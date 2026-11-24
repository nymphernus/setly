package com.nmph.setly.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.WorkoutPlan
import com.nmph.setly.ui.components.ClockSection
import com.nmph.setly.ui.components.EmptyStateView
import com.nmph.setly.ui.components.SetPill
import com.nmph.setly.ui.components.WorkoutClock
import com.nmph.setly.ui.components.clockColor
import com.nmph.setly.ui.theme.AppCards
import com.nmph.setly.ui.theme.AppSpacing
import com.nmph.setly.ui.viewmodel.ExerciseTotal
import com.nmph.setly.ui.viewmodel.TodayViewModel
import com.nmph.setly.ui.viewmodel.WorkoutDetailViewModel
import com.nmph.setly.utils.DistributionHelper
import com.nmph.setly.utils.TimeWindow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    workout: WorkoutPlan?,
    todayViewModel: TodayViewModel,
    detailViewModel: WorkoutDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val todayWorkouts by todayViewModel.todayWorkouts.collectAsStateWithLifecycle()
    val totals by detailViewModel.totals.collectAsStateWithLifecycle()
    val adjustedTargets by detailViewModel.adjustedTargets.collectAsStateWithLifecycle()
    val todayItem = todayWorkouts.firstOrNull { it.workout.id == workout?.workout?.id }
    val todayExercises = todayItem?.exercises?.associateBy { it.exercise.id }.orEmpty()

    val totalSets = todayItem?.totalSets ?: 0
    val completedSets = todayItem?.completedSets ?: 0
    val isDone = todayItem?.isDone == true
    val targetReps = todayItem?.exercises?.sumOf { it.dailyTargetReps } ?: 0
    val doneReps = todayItem?.exercises?.sumOf { it.completedToday } ?: 0
    val remainingReps = (targetReps - doneReps).coerceAtLeast(0)

    val sleepStart = workout?.workout?.sleepStart ?: "23:00"
    val sleepEnd = workout?.workout?.sleepEnd ?: "07:00"

    // Каждое упражнение живёт своей жизнью: свои подходы, свои окна, свои долги.
    val exerciseViews = workout?.exercises?.mapIndexed { index, planned ->
        val todayExercise = todayExercises[planned.exercise.id]
        val target = adjustedTargets[planned.exercise.id] ?: planned.dailyTargetReps
        val sets = todayExercise?.sets
            ?: DistributionHelper.setsForAdjustedTarget(planned, target)
        val dailyTarget = todayExercise?.adjustedTargetReps ?: target
        val windows = DistributionHelper.calculateWindowsForExercise(sleepStart, sleepEnd, sets.size)
        ExerciseView(
            index = index,
            exercise = planned.exercise,
            dailyTargetReps = dailyTarget,
            baseTargetReps = planned.dailyTargetReps,
            sets = sets,
            windows = windows,
            completedIndexes = todayExercise?.completedIndexes.orEmpty(),
            periodDays = workout.workout.periodDays,
            total = totals.byExercise[planned.exercise.id],
        )
    }.orEmpty()

    val clockSections = exerciseViews.map { view ->
        ClockSection(
            exerciseId = view.exercise.id,
            label = view.exercise.name,
            color = clockColor(view.index),
            windows = view.windows,
        )
    }

    Scaffold(
        modifier = modifier,
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
                                "Период: ${workout.workout.periodDays} дней · Сон: $sleepStart - $sleepEnd",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            "Выполнено $completedSets из $totalSets подходов",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Осталось $remainingReps повторений",
                            style = MaterialTheme.typography.bodySmall,
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
                WorkoutClock(
                    sections = clockSections,
                    sleepStart = sleepStart,
                    sleepEnd = sleepEnd,
                )
            }

            if (exerciseViews.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.FitnessCenter,
                        title = "Добавьте упражнения в эту тренировку",
                        description = "Без упражнений нечего распределять по дням и отмечать в подходах.",
                        actionText = "Добавить упражнение",
                        onAction = onEdit,
                    )
                }
            } else {
                items(exerciseViews, key = { it.exercise.id }) { view ->
                    ExerciseSetsCard(
                        view = view,
                        onSetClick = { index ->
                            todayViewModel.toggleSet(view.exercise.id, index)
                        },
                    )
                }
            }
        }
    }
}

private data class ExerciseView(
    val index: Int,
    val exercise: ExerciseEntity,
    val dailyTargetReps: Int,
    val baseTargetReps: Int,
    val sets: List<Int>,
    val windows: List<TimeWindow>,
    val completedIndexes: List<Int>,
    val periodDays: Int,
    val total: ExerciseTotal?,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseSetsCard(
    view: ExerciseView,
    onSetClick: (index: Int) -> Unit,
) {
    val isDone = view.sets.isNotEmpty() && view.completedIndexes.size == view.sets.size
    val backgroundColor by animateColorAsState(
        targetValue = if (isDone) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(250),
        label = "detailExerciseBackground",
    )
    val completedReps = view.total?.completedReps ?: 0
    val possibleReps = view.dailyTargetReps * view.periodDays
    val percent = if (possibleReps > 0) {
        ((completedReps * 100) / possibleReps).coerceIn(0, 100)
    } else {
        0
    }
    val windows = view.windows

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
            ) {
                CanvasDot(color = clockColor(view.index))
                Text(
                    "${view.exercise.name} (Цель: ${view.dailyTargetReps})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                "На сегодня: ${view.dailyTargetReps} ${view.exercise.unit} → " +
                    "${view.sets.size} подходов (с учётом долгов)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (windows.isEmpty()) {
                    "Временные окна не распределены"
                } else {
                    "Окна: ${windows.first().label} – ${windows.last().endLabel} · " +
                        "${windows.first().durationMinutes} мин на подход"
                },
                style = MaterialTheme.typography.bodySmall,
                color = clockColor(view.index),
            )
            Text(
                "Всего за период: ${view.baseTargetReps * view.periodDays} раз " +
                    "($view.periodDays дней × ${view.baseTargetReps})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Выполнено за всё время: $completedReps раз ($percent%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                "Отмечено подходов сегодня: ${view.completedIndexes.size} из ${view.sets.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isDone) {
                Text(
                    "Норма на сегодня выполнена",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (view.sets.isEmpty()) {
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
                    view.sets.forEachIndexed { index, reps ->
                        SetPill(
                            reps = reps,
                            checked = index in view.completedIndexes,
                            compact = true,
                            onClick = { onSetClick(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CanvasDot(color: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(12.dp)) {
        drawCircle(color = color)
    }
}
