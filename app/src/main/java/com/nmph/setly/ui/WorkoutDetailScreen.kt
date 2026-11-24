package com.nmph.setly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmph.setly.data.local.WorkoutPlan
import com.nmph.setly.ui.components.EmptyStateView
import com.nmph.setly.ui.components.PhaseTimer
import com.nmph.setly.ui.components.SetPill
import com.nmph.setly.ui.theme.AppCards
import com.nmph.setly.ui.theme.AppSpacing
import com.nmph.setly.ui.viewmodel.ExerciseTotal
import com.nmph.setly.ui.viewmodel.TimerPhase
import com.nmph.setly.ui.viewmodel.TodayViewModel
import com.nmph.setly.ui.viewmodel.WorkoutDetailViewModel
import com.nmph.setly.utils.DistributionHelper

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
    val timerState by todayViewModel.timer.collectAsStateWithLifecycle()
    val totals by detailViewModel.totals.collectAsStateWithLifecycle()
    val adjustedTargets by detailViewModel.adjustedTargets.collectAsStateWithLifecycle()
    val todayItem = todayWorkouts.firstOrNull { it.workout.id == workout?.workout?.id }
    val todayExercises = todayItem?.exercises?.associateBy { it.exercise.id }.orEmpty()

    var showCancelDialog by remember { mutableStateOf(false) }
    var pendingToggle by remember { mutableStateOf<Pair<() -> Unit, () -> Unit>?>(null) }

    val totalSets = todayItem?.totalSets ?: 0
    val completedSets = todayItem?.completedSets ?: 0
    val isDone = todayItem?.isDone == true
    val targetReps = todayItem?.exercises?.sumOf { it.dailyTargetReps } ?: 0
    val doneReps = todayItem?.exercises?.sumOf { it.completedToday } ?: 0
    val remainingReps = (targetReps - doneReps).coerceAtLeast(0)
    val restMinutes = if (workout == null) {
        0
    } else {
        DistributionHelper.restMinutes(workout.workout.sleepStart, workout.workout.sleepEnd, totalSets)
    }

    // Комплексные повторы: каждый круг объединяет по одному подходу каждого упражнения.
    val rounds = workout?.exercises?.map { planned ->
        val todayExercise = todayExercises[planned.exercise.id]
        val sets = todayExercise?.sets
            ?: DistributionHelper.setsForAdjustedTarget(
                planned,
                adjustedTargets[planned.exercise.id] ?: planned.dailyTargetReps,
            )
        Triple(planned, sets, todayExercise?.completedIndexes.orEmpty())
    }?.let { views ->
        (0 until (views.maxOfOrNull { it.second.size } ?: 0)).map { index ->
            Round(
                number = index + 1,
                sets = views.mapNotNull { (planned, sets, done) ->
                    sets.getOrNull(index)?.let { reps ->
                        RoundSet(
                            exerciseId = planned.exercise.id,
                            name = planned.exercise.name,
                            unit = planned.exercise.unit,
                            reps = reps,
                            index = index,
                            checked = index in done,
                        )
                    }
                },
            )
        }
    }.orEmpty()
    val completedRounds = rounds.count { it.isDone }

    /** Повторное нажатие во время таймера требует подтверждения отмены. */
    fun onToggleRequested(
        exerciseId: Int,
        reps: Int,
        unit: String,
        apply: () -> Unit,
        startTimer: Boolean,
    ) {
        val start: () -> Unit = if (startTimer) {
            { todayViewModel.startSetTimer(exerciseId, reps, unit, restMinutes) }
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
                                "Период: ${workout.workout.periodDays} дней · Сон: " +
                                    "${workout.workout.sleepStart} - ${workout.workout.sleepEnd} · " +
                                    "Отдых: $restMinutes мин",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                            if (rounds.isNotEmpty()) {
                                "Выполнено $completedRounds из ${rounds.size} кругов"
                            } else {
                                "Выполнено $completedSets из $totalSets подходов"
                            },
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
                AnimatedVisibility(
                    visible = timerState.isActive,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    PhaseTimer(state = timerState)
                }
            }

            if (workout.exercises.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.FitnessCenter,
                        title = "Добавьте упражнения в эту тренировку",
                        description = "Без упражнений нечего распределять по дням и отмечать в подходах.",
                        actionText = "Добавить упражнение",
                        onAction = onEdit,
                    )
                }
            } else if (workout.exercises.size == 1) {
                items(workout.exercises, key = { it.exercise.id }) { planned ->
                    val todayExercise = todayExercises[planned.exercise.id]
                    val plannedSets = DistributionHelper.setsForAdjustedTarget(
                        planned,
                        adjustedTargets[planned.exercise.id] ?: planned.dailyTargetReps,
                    )
                    ExerciseSetsCard(
                        name = planned.exercise.name,
                        dailyTargetReps = todayExercise?.adjustedTargetReps
                            ?: planned.dailyTargetReps,
                        baseTargetReps = planned.dailyTargetReps,
                        repsPerSet = plannedSets.firstOrNull() ?: planned.repsPerSet,
                        sets = todayExercise?.sets ?: plannedSets,
                        completedIndexes = todayExercise?.completedIndexes.orEmpty(),
                        periodDays = workout.workout.periodDays,
                        total = totals.byExercise[planned.exercise.id],
                        onSetClick = { index, wasChecked ->
                            val reps = todayExercise?.sets?.getOrNull(index)
                                ?: plannedSets.getOrNull(index)
                                ?: planned.repsPerSet
                            onToggleRequested(
                                exerciseId = planned.exercise.id,
                                reps = reps,
                                unit = planned.exercise.unit,
                                apply = { todayViewModel.toggleSet(planned.exercise.id, index) },
                                startTimer = !wasChecked,
                            )
                        },
                    )
                }
            } else {
                item {
                    Text(
                        "Комплексные повторы: ${rounds.size} кругов по ${workout.exercises.size} " +
                            "упражнений. Отметьте круг целиком или отдельные подходы.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(rounds, key = { it.number }) { round ->
                    RoundCard(
                        round = round,
                        onRoundClick = {
                            val first = round.sets.firstOrNull()
                            onToggleRequested(
                                exerciseId = first?.exerciseId ?: -1,
                                reps = first?.reps ?: 0,
                                unit = first?.unit ?: "",
                                apply = {
                                    todayViewModel.toggleRound(
                                        round.sets.map { it.exerciseId to it.index },
                                    )
                                },
                                startTimer = !round.isDone,
                            )
                        },
                        onSetClick = { item, wasChecked ->
                            onToggleRequested(
                                exerciseId = item.exerciseId,
                                reps = item.reps ?: 0,
                                unit = item.unit,
                                apply = { todayViewModel.toggleSet(item.exerciseId, item.index) },
                                startTimer = !wasChecked,
                            )
                        },
                    )
                }
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
                        todayViewModel.cancelTimer()
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
private fun ExerciseSetsCard(
    name: String,
    dailyTargetReps: Int,
    baseTargetReps: Int,
    repsPerSet: Int,
    sets: List<Int>,
    completedIndexes: List<Int>,
    periodDays: Int,
    total: ExerciseTotal?,
    onSetClick: (index: Int, wasChecked: Boolean) -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (sets.isNotEmpty() && completedIndexes.size == sets.size) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(250),
        label = "detailExerciseBackground",
    )
    val isDone = sets.isNotEmpty() && completedIndexes.size == sets.size
    val completedReps = total?.completedReps ?: 0
    val possibleReps = dailyTargetReps * periodDays
    val percent = if (possibleReps > 0) {
        ((completedReps * 100) / possibleReps).coerceIn(0, 100)
    } else {
        0
    }
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
                name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                "На сегодня: $dailyTargetReps раз → ${sets.size} подходов по $repsPerSet раз (с учётом долгов)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Всего за период: ${baseTargetReps * periodDays} раз ($periodDays дней × $baseTargetReps)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Выполнено за всё время: $completedReps раз ($percent%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                "Отмечено подходов сегодня: ${completedIndexes.size} из ${sets.size}",
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
            if (sets.isEmpty()) {
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
                    sets.forEachIndexed { index, reps ->
                        val isChecked = index in completedIndexes
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

/** Один подход упражнения внутри круга. */
private data class RoundSet(
    val exerciseId: Int,
    val name: String,
    val unit: String,
    val reps: Int?,
    val index: Int,
    val checked: Boolean,
)

/** Круг: набор подходов, которые выполняются вместе. */
private data class Round(
    val number: Int,
    val sets: List<RoundSet>,
) {
    val isDone: Boolean get() = sets.isNotEmpty() && sets.all { it.checked }
}

@Composable
private fun RoundCard(
    round: Round,
    onRoundClick: () -> Unit,
    onSetClick: (RoundSet, Boolean) -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (round.isDone) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surface,
        animationSpec = tween(250),
        label = "roundBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (round.isDone) Color.White else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(250),
        label = "roundContent",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = AppCards.Shape, // RoundedCornerShape(16.dp)
        elevation = CardDefaults.cardElevation(defaultElevation = AppCards.Elevation), // 2.dp
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
        ),
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
            ) {
                Checkbox(
                    checked = round.isDone,
                    onCheckedChange = { onRoundClick() },
                )
                Text(
                    "Круг ${round.number}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (round.isDone) Color.White else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onRoundClick),
                )
                Text(
                    "${round.sets.count { it.checked }}/${round.sets.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (round.isDone) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = !round.isDone,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner)) {
                    round.sets.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = item.checked,
                                onCheckedChange = { onSetClick(item, item.checked) },
                            )
                            Text(
                                "${item.name} · цель: ${item.reps ?: 0} ${item.unit}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSetClick(item, item.checked) },
                            )
                        }
                    }
                }
            }
            if (round.isDone) {
                Text(
                    "Круг выполнен",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}
