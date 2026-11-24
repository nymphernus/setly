package com.nmph.setly.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
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
        if (items.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.DateRange,
                title = "Нет данных за этот день",
                description = "В этот период нет активных тренировок. Выберите другой день в календаре.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(AppSpacing.Block),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Block),
        ) {
            items(items, key = { it.workout.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppCards.Shape,
                    elevation = CardDefaults.cardElevation(defaultElevation = AppCards.Elevation),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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

@Composable
private fun DayExerciseRow(item: DayExerciseItem) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = AppSpacing.CardInner),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(item.exercise.name, style = MaterialTheme.typography.titleSmall)
        if (item.exercise.description.isNotBlank()) {
            Text(
                item.exercise.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Цель: ${item.dailyTargetReps} раз → ${item.sets.size} подходов по ${item.repsPerSet}",
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
