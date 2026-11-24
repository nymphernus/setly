package com.nmph.setly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nmph.setly.data.local.ExerciseEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditExerciseScreen(
    exercise: ExerciseEntity?,
    onSave: (name: String, description: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = rememberSaveable { mutableStateOf(exercise?.name.orEmpty()) }
    val description = rememberSaveable { mutableStateOf(exercise?.description.orEmpty()) }
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (exercise == null) "Новое упражнение" else "Редактирование упражнения") },
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
                "Опишите упражнение один раз — затем добавляйте его в тренировки. " +
                    "Дневную норму и размер подхода зададите в тренировке.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name.value,
                onValueChange = { name.value = it },
                label = { Text("Название") },
                placeholder = { Text("Например, Отжимания") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = description.value,
                onValueChange = { description.value = it },
                label = { Text("Описание (необязательно)") },
                placeholder = { Text("Например, грудь и трицепс") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )

            Button(
                onClick = {
                    if (name.value.isNotBlank()) {
                        onSave(name.value.trim(), description.value.trim())
                    }
                },
                enabled = name.value.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить")
            }
        }
    }
}
