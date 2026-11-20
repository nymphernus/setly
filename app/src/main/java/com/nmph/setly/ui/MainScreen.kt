package com.nmph.setly.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.ui.viewmodel.TodayViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    exercises: List<ExerciseEntity>,
    todayViewModel: TodayViewModel,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (ExerciseEntity) -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedTab = remember { mutableStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Мои тренировки") },
                actions = {
                    IconButton(onClick = onProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Профиль")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Добавить упражнение")
            }
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab.value) {
                Tab(
                    selected = selectedTab.value == 0,
                    onClick = { selectedTab.value = 0 },
                    text = { Text("Сегодня") },
                )
                Tab(
                    selected = selectedTab.value == 1,
                    onClick = { selectedTab.value = 1 },
                    text = { Text("Все упражнения") },
                )
            }
            when (selectedTab.value) {
                0 -> TodayScreen(viewModel = todayViewModel)
                1 -> ExerciseList(exercises, onEdit, onDelete)
            }
        }
    }
}

@Composable
private fun ExerciseList(
    exercises: List<ExerciseEntity>,
    onEdit: (Int) -> Unit,
    onDelete: (ExerciseEntity) -> Unit,
) {
    if (exercises.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Пока нет упражнений", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            items(exercises, key = { it.id }) { exercise ->
                ExerciseCard(exercise, onEdit, onDelete)
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseEntity,
    onEdit: (Int) -> Unit,
    onDelete: (ExerciseEntity) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(exercise.id) },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "${exercise.name} — ${exercise.targetReps} раз за ${exercise.periodDays} дней",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Сон: ${exercise.sleepHours} ч",
                style = MaterialTheme.typography.bodySmall,
            )
            IconButton(onClick = { onDelete(exercise) }) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить")
            }
        }
    }
}
