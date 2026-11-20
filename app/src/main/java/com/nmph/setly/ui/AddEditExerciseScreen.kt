package com.nmph.setly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditExerciseScreen(
    exercise: ExerciseEntity?,
    onSave: (name: String, targetReps: Int, periodDays: Int, sleepHours: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = remember { mutableStateOf(exercise?.name.orEmpty()) }
    val reps = remember { mutableStateOf(exercise?.targetReps?.toString().orEmpty()) }
    val period = remember { mutableStateOf(exercise?.periodDays?.toString().orEmpty()) }
    val sleep = remember { mutableStateOf(exercise?.sleepHours?.toString().orEmpty()) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (exercise == null) "Новое упражнение" else "Редактирование") },
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
            NumberField("Количество повторений", reps.value) { reps.value = it }
            NumberField("Период в днях", period.value) { period.value = it }
            NumberField("Часов сна", sleep.value) { sleep.value = it }

            val canSave = name.value.isNotBlank() && reps.value.toIntOrNull() != null &&
                period.value.toIntOrNull() != null && sleep.value.toIntOrNull() != null
            Button(
                onClick = {
                    if (canSave) {
                        onSave(
                            name.value.trim(),
                            reps.value.toInt(),
                            period.value.toInt(),
                            sleep.value.toInt(),
                        )
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить")
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    // Фильтруем нецифровые символы прямо в поле, чтобы пользователь не мог ввести текст.
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onChange(raw.filter { it.isDigit() }) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
