package com.nmph.setly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.WorkoutEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditWorkoutScreen(
    workout: WorkoutEntity?,
    availableExercises: List<ExerciseEntity>,
    initialSelectedIds: Set<Int> = emptySet(),
    onSave: (
        name: String,
        periodDays: Int,
        sleepStart: String,
        sleepEnd: String,
        restTimeMinutes: Int,
        selectedIds: List<Int>,
    ) -> Unit,
    onCreateExercise: (name: String, reps: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = remember { mutableStateOf(workout?.name.orEmpty()) }
    val period = remember { mutableStateOf(workout?.periodDays?.toString().orEmpty()) }
    val sleepStart = remember { mutableStateOf(workout?.sleepStart ?: "21:00") }
    val sleepEnd = remember { mutableStateOf(workout?.sleepEnd ?: "08:00") }
    val restMinutes = remember { mutableStateOf(workout?.restTimeMinutes?.toString() ?: "30") }
    val selected = remember { mutableStateOf(initialSelectedIds) }
    val newName = remember { mutableStateOf("") }
    val newReps = remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (workout == null) "Новая тренировка" else "Редактирование") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name.value,
                onValueChange = { name.value = it },
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            NumberField("Период в днях", period.value) { period.value = it }
            OutlinedTextField(
                value = sleepStart.value,
                onValueChange = { sleepStart.value = it.take(5) },
                label = { Text("Сон начало (HH:mm)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = sleepEnd.value,
                onValueChange = { sleepEnd.value = it.take(5) },
                label = { Text("Сон конец (HH:mm)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            NumberField("Отдых между подходами (мин)", restMinutes.value) { restMinutes.value = it }

            Text("Упражнения", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f)) {
                items(availableExercises, key = { it.id }) { exercise ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            checked = selected.value.contains(exercise.id),
                            onCheckedChange = { checked ->
                                selected.value = if (checked) selected.value + exercise.id else selected.value - exercise.id
                            },
                        )
                        Text("${exercise.name} — ${exercise.targetReps} раз")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newName.value,
                    onValueChange = { newName.value = it },
                    label = { Text("Новое упражнение") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = newReps.value,
                    onValueChange = { newReps.value = it.filter { c -> c.isDigit() } },
                    label = { Text("Повторы") },
                    modifier = Modifier.weight(0.6f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Button(
                    onClick = {
                        val reps = newReps.value.toIntOrNull()
                        if (newName.value.isNotBlank() && reps != null) {
                            onCreateExercise(newName.value.trim(), reps)
                            newName.value = ""
                            newReps.value = ""
                        }
                    },
                ) { Text("+") }
            }

            val canSave = name.value.isNotBlank() && period.value.toIntOrNull() != null &&
                restMinutes.value.toIntOrNull() != null &&
                isTimeValid(sleepStart.value) && isTimeValid(sleepEnd.value)
            Button(
                onClick = {
                    if (canSave) {
                        onSave(
                            name.value.trim(),
                            period.value.toInt(),
                            sleepStart.value,
                            sleepEnd.value,
                            restMinutes.value.toInt(),
                            selected.value.toList(),
                        )
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
        }
    }
}

private fun isTimeValid(value: String): Boolean {
    val parts = value.split(":")
    if (parts.size != 2 || parts[0].length != 2 || parts[1].length != 2) return false
    val h = parts[0].toIntOrNull() ?: return false
    val m = parts[1].toIntOrNull() ?: return false
    return h in 0..23 && m in 0..59
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onChange(raw.filter { it.isDigit() }) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
