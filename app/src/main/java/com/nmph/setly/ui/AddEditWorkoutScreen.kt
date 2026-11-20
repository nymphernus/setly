package com.nmph.setly.ui

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.utils.DistributionHelper

private data class SelectedExerciseInput(
    val exerciseId: Int,
    val targetReps: String,
)

private val SelectedExercisesSaver = Saver<MutableState<List<SelectedExerciseInput>>, List<Pair<Int, String>>>(
    save = { state -> state.value.map { it.exerciseId to it.targetReps } },
    restore = { saved -> mutableStateOf(saved.map { SelectedExerciseInput(it.first, it.second) }) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditWorkoutScreen(
    workout: WorkoutEntity?,
    availableExercises: List<ExerciseEntity>,
    initialSelectedIds: Set<Int> = emptySet(),
    initialTargets: Map<Int, Int> = emptyMap(),
    onSave: (
        name: String,
        periodDays: Int,
        sleepStart: String,
        sleepEnd: String,
        restTimeMinutes: Int,
        autoRestTime: Boolean,
        targets: Map<Int, Int>,
    ) -> Unit,
    onBack: () -> Unit,
    autoRestEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val name = rememberSaveable { mutableStateOf(workout?.name.orEmpty()) }
    val period = rememberSaveable { mutableStateOf(workout?.periodDays?.toString().orEmpty()) }
    val restMinutes = rememberSaveable {
        mutableStateOf(workout?.restTimeMinutes?.toString() ?: DistributionHelper.DEFAULT_REST_MINUTES.toString())
    }
    val autoRest = rememberSaveable { mutableStateOf(workout?.autoRestTime ?: true) }
    val selectedExercises = rememberSaveable(saver = SelectedExercisesSaver) {
        mutableStateOf(emptyList<SelectedExerciseInput>())
    }
    val initializedWorkoutId = rememberSaveable { mutableStateOf(Int.MIN_VALUE) }
    val scrollState = rememberScrollState()

    // Данные приходят асинхронно. Не трогаем сохранённый ввод после поворота экрана.
    LaunchedEffect(workout, initialSelectedIds, initialTargets, availableExercises) {
        val incomingId = workout?.id ?: -1
        if (initializedWorkoutId.value == incomingId) return@LaunchedEffect
        name.value = workout?.name.orEmpty()
        period.value = workout?.periodDays?.toString() ?: "30"
        restMinutes.value = workout?.restTimeMinutes?.toString()
            ?: DistributionHelper.DEFAULT_REST_MINUTES.toString()
        autoRest.value = workout?.autoRestTime ?: true
        selectedExercises.value = initialSelectedIds.map { id ->
            val stored = initialTargets[id]
                ?: availableExercises.firstOrNull { it.id == id }?.targetReps
                ?: 0
            val value = if (stored > 0) stored else DistributionHelper.DEFAULT_TARGET_REPS
            SelectedExerciseInput(id, value.toString())
        }
        initializedWorkoutId.value = incomingId
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (workout == null) "Новая тренировка" else "Редактирование тренировки") },
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
            Text(
                "Тренировка распределяет выбранные упражнения по дням. " +
                    "Для каждого упражнения задайте общее число повторений за весь период.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = name.value,
                onValueChange = { name.value = it },
                label = { Text("Название тренировки") },
                placeholder = { Text("Например, МаксОтжим") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            NumberField(
                label = "Период в днях",
                value = period.value,
                placeholder = "30",
                onChange = { period.value = it },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = autoRest.value,
                    onCheckedChange = { autoRest.value = it },
                )
                Column {
                    Text("Отдых рассчитывать автоматически")
                    Text(
                        "24 часа делятся на число подходов в день, максимум ${DistributionHelper.MAX_REST_MINUTES} мин",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!autoRest.value) {
                NumberField(
                    label = "Отдых между подходами (мин)",
                    value = restMinutes.value,
                    placeholder = "30",
                    onChange = { restMinutes.value = it },
                )
            }
            if (!autoRestEnabled && workout?.autoRestTime == true) {
                Text(
                    "В профиле выключен автоматический отдых — используется ручное значение.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Text("Упражнения", style = MaterialTheme.typography.titleMedium)
            if (availableExercises.isEmpty()) {
                Text(
                    "Сначала создайте упражнения на вкладке «Упражнения».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableExercises.forEach { exercise ->
                        val input = selectedExercises.value.firstOrNull { it.exerciseId == exercise.id }
                        ExerciseSelectionRow(
                            exercise = exercise,
                            checked = input != null,
                            targetReps = input?.targetReps.orEmpty(),
                            onCheckedChange = { checked ->
                                selectedExercises.value = if (checked) {
                                    val defaultTarget = if (exercise.targetReps > 0) {
                                        exercise.targetReps.toString()
                                    } else {
                                        DistributionHelper.DEFAULT_TARGET_REPS.toString()
                                    }
                                    selectedExercises.value + SelectedExerciseInput(exercise.id, defaultTarget)
                                } else {
                                    selectedExercises.value.filterNot { it.exerciseId == exercise.id }
                                }
                            },
                            onTargetChange = { value ->
                                selectedExercises.value = selectedExercises.value.map {
                                    if (it.exerciseId == exercise.id) it.copy(targetReps = value) else it
                                }
                            },
                        )
                    }
                }
            }

            val targetsValid = selectedExercises.value.isNotEmpty() &&
                selectedExercises.value.all { (it.targetReps.toIntOrNull() ?: 0) > 0 }
            val canSave = name.value.isNotBlank() &&
                (period.value.toIntOrNull() ?: 0) > 0 &&
                (restMinutes.value.toIntOrNull() ?: 0) > 0 &&
                targetsValid

            if (!targetsValid) {
                Text(
                    "Выберите хотя бы одно упражнение и укажите положительную цель повторений.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            androidx.compose.material3.Button(
                onClick = {
                    if (canSave) {
                        onSave(
                            name.value.trim(),
                            period.value.toInt(),
                            workout?.sleepStart ?: "21:00",
                            workout?.sleepEnd ?: "08:00",
                            restMinutes.value.toInt(),
                            autoRest.value,
                            selectedExercises.value.associate {
                                it.exerciseId to (it.targetReps.toIntOrNull() ?: 0)
                            },
                        )
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
        }
    }
}

@Composable
private fun ExerciseSelectionRow(
    exercise: ExerciseEntity,
    checked: Boolean,
    targetReps: String,
    onCheckedChange: (Boolean) -> Unit,
    onTargetChange: (String) -> Unit,
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
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                Column(modifier = Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${exercise.repsPerSet} повторений в подходе",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (checked) {
                    IconButton(onClick = { onCheckedChange(false) }) {
                        Icon(Icons.Default.Close, contentDescription = "Убрать из тренировки")
                    }
                }
            }
            if (checked) {
                NumberField(
                    label = "Всего повторений за период",
                    value = targetReps,
                    placeholder = "300",
                    onChange = onTargetChange,
                )
            }
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
