package com.nmph.setly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
            Text("Нет активных тренировок на сегодня", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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

    LaunchedEffect(timerRun) {
        if (timerRun == 0) return@LaunchedEffect
        var remaining = item.workout.restTimeMinutes.coerceAtLeast(0) * 60
        if (remaining == 0) return@LaunchedEffect
        timeLeft = remaining
        while (remaining > 0) {
            delay(1_000)
            remaining--
            timeLeft = remaining
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.workout.name, style = MaterialTheme.typography.titleMedium)
            if (timerRun > 0) {
                val timerText = if (timeLeft > 0) {
                    "Отдых: %02d:%02d".format(timeLeft / 60, timeLeft % 60)
                } else {
                    "Отдых окончен!"
                }
                Text(
                    timerText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (timeLeft > 0) MaterialTheme.colorScheme.primary else Color(0xFF2E7D32),
                )
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

@Composable
private fun TodayExerciseRow(
    item: TodayExerciseItem,
    viewModel: TodayViewModel,
    onSetChecked: () -> Unit,
) {
    val done = item.sets.isNotEmpty() && item.completedSets == item.sets
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (done) Modifier.background(Color(0xFFC8E6C9)) else Modifier)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.exercise.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${item.completedToday} / ${item.targetForToday}")
            if (done) {
                Text("Выполнено", color = Color(0xFF2E7D32), modifier = Modifier.padding(start = 8.dp))
            }
        }
        // Много подходов (например, 15) не должны растягивать экран — прокручиваем внутри карточки.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            item.sets.forEachIndexed { index, reps ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.completedSets.getOrNull(index) == reps,
                        onCheckedChange = { checked ->
                            viewModel.toggleSet(item.exercise.id, index)
                            if (checked) onSetChecked()
                        },
                    )
                    Text("$reps раз")
                }
            }
        }
    }
}
