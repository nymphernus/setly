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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nmph.setly.data.local.ExerciseEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditExerciseScreen(
    exercise: ExerciseEntity?,
    onSave: (name: String, category: String, unit: String, iconResName: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = rememberSaveable { mutableStateOf(exercise?.name.orEmpty()) }
    val category = rememberSaveable {
        mutableStateOf(exercise?.category?.ifBlank { ExerciseCatalog.defaultCategory() }
            ?: ExerciseCatalog.defaultCategory())
    }
    val unit = rememberSaveable {
        mutableStateOf(exercise?.unit?.ifBlank { ExerciseCatalog.defaultUnit() }
            ?: ExerciseCatalog.defaultUnit())
    }
    val iconResName = rememberSaveable {
        mutableStateOf(exercise?.iconResName?.ifBlank { ExerciseCatalog.defaultIcon() }
            ?: ExerciseCatalog.defaultIcon())
    }
    val initializedId = rememberSaveable { mutableStateOf(Int.MIN_VALUE) }
    val scrollState = rememberScrollState()

    LaunchedEffect(exercise?.id) {
        val incomingId = exercise?.id ?: -1
        if (initializedId.value == incomingId) return@LaunchedEffect
        exercise?.let {
            name.value = it.name
            category.value = it.category.ifBlank { ExerciseCatalog.defaultCategory() }
            unit.value = it.unit.ifBlank { ExerciseCatalog.defaultUnit() }
            iconResName.value = it.iconResName.ifBlank { ExerciseCatalog.defaultIcon() }
        }
        initializedId.value = incomingId
    }

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
                "Категория, единица измерения и иконка помогут быстрее находить упражнение " +
                    "и понимать, как считать подход.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExerciseEditorFields(
                name = name.value,
                onNameChange = { name.value = it },
                category = category.value,
                onCategoryChange = { category.value = it },
                unit = unit.value,
                onUnitChange = { unit.value = it },
                iconResName = iconResName.value,
                onIconChange = { iconResName.value = it },
            )

            Button(
                onClick = {
                    if (name.value.isNotBlank()) {
                        onSave(name.value.trim(), category.value, unit.value, iconResName.value)
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
