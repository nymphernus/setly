package com.nmph.setly.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.WorkoutPlan
import com.nmph.setly.utils.DistributionHelper

private data class SelectedExerciseInput(
    val exerciseId: Int,
    val dailyTargetReps: String,
)

private val SelectedExercisesSaver = Saver<MutableState<List<SelectedExerciseInput>>, List<Pair<Int, String>>>(
    save = { state -> state.value.map { it.exerciseId to it.dailyTargetReps } },
    restore = { saved -> mutableStateOf(saved.map { SelectedExerciseInput(it.first, it.second) }) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditWorkoutScreen(
    workout: WorkoutPlan?,
    availableExercises: List<ExerciseEntity>,
    initialSelectedIds: Set<Int> = emptySet(),
    initialTargets: Map<Int, Int> = emptyMap(),
    onSave: (
        name: String,
        periodDays: Int,
        sleepStart: String,
        sleepEnd: String,
        dailyTargets: Map<Int, Int>,
    ) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = rememberSaveable { mutableStateOf(workout?.workout?.name.orEmpty()) }
    val period = rememberSaveable {
        mutableStateOf(workout?.workout?.periodDays?.toString() ?: "30")
    }
    val sleepStart = rememberSaveable {
        mutableStateOf(workout?.workout?.sleepStart ?: "22:00")
    }
    val sleepEnd = rememberSaveable {
        mutableStateOf(workout?.workout?.sleepEnd ?: "08:00")
    }
    val selectedExercises = rememberSaveable(saver = SelectedExercisesSaver) {
        mutableStateOf(emptyList<SelectedExerciseInput>())
    }
    val initializedWorkoutId = rememberSaveable { mutableStateOf(Int.MIN_VALUE) }
    val showExercisePicker = rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // План приходит асинхронно; не перезаписываем уже введённые данные при повороте экрана.
    LaunchedEffect(workout?.workout?.id, initialSelectedIds, initialTargets) {
        val incomingId = workout?.workout?.id ?: -1
        if (initializedWorkoutId.value == incomingId) return@LaunchedEffect
        name.value = workout?.workout?.name.orEmpty()
        period.value = workout?.workout?.periodDays?.toString() ?: "30"
        sleepStart.value = workout?.workout?.sleepStart ?: "22:00"
        sleepEnd.value = workout?.workout?.sleepEnd ?: "08:00"
        val ids = (initialSelectedIds + initialTargets.keys).toList()
        selectedExercises.value = ids.map { id ->
            SelectedExerciseInput(
                exerciseId = id,
                dailyTargetReps = (initialTargets[id] ?: DistributionHelper.DEFAULT_DAILY_TARGET_REPS).toString(),
            )
        }
        initializedWorkoutId.value = incomingId
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (workout == null) "Новая тренировка" else "Редактирование тренировки")
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
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name.value,
                onValueChange = { name.value = it },
                label = { Text("Название тренировки") },
                placeholder = { Text("Введите название") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            NumberField(
                label = "Период в днях",
                value = period.value,
                placeholder = "30",
                onChange = { period.value = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField(
                    label = "Сон начало",
                    value = sleepStart.value,
                    placeholder = "22:00",
                    onChange = { sleepStart.value = it },
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    label = "Сон конец",
                    value = sleepEnd.value,
                    placeholder = "08:00",
                    onChange = { sleepEnd.value = it },
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = { showExercisePicker.value = true },
                enabled = availableExercises.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Добавить упражнение")
            }

            val selectedIds = selectedExercises.value.map { it.exerciseId }.toSet()
            val selected = availableExercises.filter { it.id in selectedIds }
            selected.forEach { exercise ->
                val input = selectedExercises.value.first { it.exerciseId == exercise.id }
                SelectedExerciseCard(
                    exercise = exercise,
                    dailyTargetReps = input.dailyTargetReps,
                    onTargetChange = { value ->
                        selectedExercises.value = selectedExercises.value.map {
                            if (it.exerciseId == exercise.id) {
                                it.copy(dailyTargetReps = value)
                            } else {
                                it
                            }
                        }
                    },
                    onRemove = {
                        selectedExercises.value = selectedExercises.value.filterNot {
                            it.exerciseId == exercise.id
                        }
                    },
                )
            }

            val targetsValid = selectedExercises.value.isNotEmpty() &&
                selectedExercises.value.all { (it.dailyTargetReps.toIntOrNull() ?: 0) > 0 }
            val canSave = name.value.isNotBlank() &&
                (period.value.toIntOrNull() ?: 0) > 0 &&
                isValidTime(sleepStart.value) &&
                isValidTime(sleepEnd.value) &&
                targetsValid

            if (!targetsValid) {
                Text(
                    "Добавьте упражнение и укажите, сколько раз в день его нужно делать.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    if (canSave) {
                        onSave(
                            name.value.trim(),
                            period.value.toInt(),
                            sleepStart.value,
                            sleepEnd.value,
                            selectedExercises.value.associate {
                                it.exerciseId to (it.dailyTargetReps.toIntOrNull() ?: 0)
                            },
                        )
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
        }
    }

    if (showExercisePicker.value) {
        ModalBottomSheet(onDismissRequest = { showExercisePicker.value = false }) {
            val alreadyAdded = selectedExercises.value.map { it.exerciseId }.toSet()
            val candidates = availableExercises.filterNot { it.id in alreadyAdded }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Выберите упражнение",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                if (candidates.isEmpty()) {
                    Text(
                        "Все созданные упражнения уже добавлены.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    candidates.forEach { exercise ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedExercises.value = selectedExercises.value + SelectedExerciseInput(
                                        exerciseId = exercise.id,
                                        dailyTargetReps = DistributionHelper.DEFAULT_DAILY_TARGET_REPS.toString(),
                                    )
                                    showExercisePicker.value = false
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Подход по ${exercise.repsPerSet} раз",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedExerciseCard(
    exercise: ExerciseEntity,
    dailyTargetReps: String,
    onTargetChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Подход по ${exercise.repsPerSet} раз",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Убрать упражнение")
                }
            }
            NumberField(
                label = "Сколько раз в день",
                value = dailyTargetReps,
                placeholder = DistributionHelper.DEFAULT_DAILY_TARGET_REPS.toString(),
                onChange = onTargetChange,
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onChange(raw.filter { it.isDigit() }) },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun TimeField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            onChange(raw.filter { it.isDigit() || it == ':' }.take(5))
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

private fun isValidTime(value: String): Boolean {
    val parts = value.split(":")
    if (parts.size != 2 || parts[0].length != 2 || parts[1].length != 2) return false
    val hours = parts[0].toIntOrNull() ?: return false
    val minutes = parts[1].toIntOrNull() ?: return false
    return hours in 0..23 && minutes in 0..59
}
