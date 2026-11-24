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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmph.setly.data.local.WorkoutPlan
import com.nmph.setly.ui.components.EmptyStateView
import com.nmph.setly.ui.theme.AppCards
import com.nmph.setly.ui.theme.AppSpacing
import com.nmph.setly.ui.viewmodel.DayDetailViewModel
import com.nmph.setly.ui.viewmodel.DayExerciseItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    date: String,
    viewModel: DayDetailViewModel,
    workouts: List<WorkoutPlan> = emptyList(),
    onFillDay: (WorkoutPlan) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var showWorkoutPicker by remember { mutableStateOf(false) }
    val title = runCatching { LocalDate.parse(date) }
        .getOrNull()
        ?.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")))
        ?: date

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppSpacing.Block, vertical = AppSpacing.CardInner),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
        ) {
            Button(
                onClick = { showWorkoutPicker = true },
                enabled = workouts.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Заполнить день тренировкой")
            }

            if (items.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.DateRange,
                    title = "Нет данных за этот день",
                    description = "Выберите тренировку выше, чтобы добавить её в этот день, " +
                        "или откройте другой день в календаре.",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(vertical = AppSpacing.CardInner),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Block),
                ) {
                    items(items, key = { it.workout.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppCards.Shape,
                            elevation = CardDefaults.cardElevation(defaultElevation = AppCards.Elevation),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(AppSpacing.Block),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
                            ) {
                                Text(
                                    item.workout.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                if (item.exercises.isEmpty()) {
                                    Text(
                                        "В тренировке нет упражнений",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                item.exercises.forEach { exercise ->
                                    DayExerciseRow(exercise)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWorkoutPicker) {
        AlertDialog(
            onDismissRequest = { showWorkoutPicker = false },
            title = { Text("Выберите тренировку") },
            text = {
                LazyColumn {
                    items(workouts, key = { it.workout.id }) { plan ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onFillDay(plan)
                                    showWorkoutPicker = false
                                }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(plan.workout.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${plan.exercises.size} упражнений · ${plan.workout.periodDays} дней",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWorkoutPicker = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun DayExerciseRow(item: DayExerciseItem) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = AppSpacing.CardInner),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(item.exercise.name, style = MaterialTheme.typography.titleSmall)
        Text(
            "На сегодня: ${item.adjustedTargetReps} раз → ${item.sets.size} подходов по " +
                "${item.sets.firstOrNull() ?: item.repsPerSet} раз (с учётом долгов)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.CardInner)) {
            Text(
                "Выполнено: ${item.completedReps} раз",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                "${item.completedIndexes.size}/${item.sets.size} подходов",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
