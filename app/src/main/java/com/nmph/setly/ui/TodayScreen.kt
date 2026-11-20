package com.nmph.setly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmph.setly.ui.viewmodel.TodayExerciseItem
import com.nmph.setly.ui.viewmodel.TodayViewModel
import com.nmph.setly.ui.viewmodel.TodayWorkoutItem
import kotlinx.coroutines.delay

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
    var timeLeft by remember { mutableStateOf(0) }
    // Увеличивается при каждом отмеченном подходе — перезапускает отсчёт заново.
    var timerRun by remember { mutableStateOf(0) }
    val totalSeconds = item.workout.restTimeMinutes.coerceAtLeast(0) * 60

    LaunchedEffect(timerRun) {
        if (timerRun == 0) return@LaunchedEffect
        var remaining = totalSeconds
        if (remaining == 0) return@LaunchedEffect
        timeLeft = remaining
        while (remaining > 0) {
            delay(1_000)
            remaining--
            timeLeft = remaining
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

            AnimatedVisibility(
                visible = timerRun > 0 && totalSeconds > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.shapes.medium,
                        )
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { timeLeft.toFloat() / totalSeconds },
                            modifier = Modifier.size(88.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeWidth = 8.dp,
                        )
                        Text(
                            if (timeLeft > 0) "%02d:%02d".format(timeLeft / 60, timeLeft % 60)
                            else "0:00",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(
                        if (timeLeft > 0) "Отдых" else "Отдых окончен!",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            item.exercises.forEach { exercise ->
                TodayExerciseRow(
                    item = exercise,
                    viewModel = viewModel,
                    onSetChecked = { if (item.workout.restTimeMinutes > 0) timerRun++ },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayExerciseRow(
    item: TodayExerciseItem,
    viewModel: TodayViewModel,
    onSetChecked: () -> Unit,
) {
    val done = item.sets.isNotEmpty() && item.completedSets == item.sets
    val rowColor by animateColorAsState(
        targetValue = if (done) MaterialTheme.colorScheme.primaryContainer
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
                "${item.completedToday} / ${item.targetForToday}",
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
        // FlowRow аккуратно переносит много подходов, не растягивая карточку по высоте.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item.sets.forEachIndexed { index, reps ->
                SetPill(
                    reps = reps,
                    checked = item.completedSets.getOrNull(index) == reps,
                    onClick = {
                        viewModel.toggleSet(item.exercise.id, index)
                        onSetChecked()
                    },
                )
            }
        }
    }
}

@Composable
private fun SetPill(
    reps: Int,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "setPillBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "setPillContent",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) contentColor else Color.Transparent)
                .border(2.dp, contentColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = background,
                )
            }
        }
        Text("$reps раз", style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}
