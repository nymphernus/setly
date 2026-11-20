package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.ExerciseDao
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.WorkoutDao
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.data.local.WorkoutWithExercises
import com.nmph.setly.data.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkoutViewModel(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val syncManager: SyncManager,
) : ViewModel() {
    val workouts: StateFlow<List<WorkoutWithExercises>> = workoutDao.getAllWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exercises: StateFlow<List<ExerciseEntity>> = exerciseDao.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun getWorkoutById(id: Int, onResult: (WorkoutWithExercises?) -> Unit) {
        viewModelScope.launch { onResult(workoutDao.getWorkoutById(id)) }
    }

    fun saveWorkout(
        id: Int,
        name: String,
        periodDays: Int,
        sleepStart: String,
        sleepEnd: String,
        restTimeMinutes: Int,
        autoRestTime: Boolean,
        exerciseIds: List<Int>,
        onResult: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            val existing = if (id != -1) workoutDao.getWorkoutById(id)?.workout else null
            val isNew = existing == null
            val workout = WorkoutEntity(
                id = existing?.id ?: 0,
                name = name,
                startDate = existing?.startDate ?: System.currentTimeMillis(),
                periodDays = periodDays,
                sleepStart = sleepStart,
                sleepEnd = sleepEnd,
                restTimeMinutes = restTimeMinutes,
                autoRestTime = autoRestTime,
            )
            // Сначала сохраняем локально (offline-first), затем бэкапим в облако.
            val savedId = workoutDao.saveWorkout(workout, exerciseIds)
            val savedExercises = exerciseIds.mapNotNull { exerciseDao.getExerciseById(it) }
            onResult(if (isNew) "Тренировка создана" else "Тренировка обновлена")
            pushToCloud(workout.copy(id = savedId), savedExercises)
        }
    }

    fun deleteWorkout(workout: WorkoutEntity, onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            workoutDao.delete(workout)
            onResult("Тренировка удалена")
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (syncManager.isSignedIn) {
                runCatching { syncManager.deleteWorkoutFromCloud(workout.id) }
            }
        }
    }

    fun createExercise(name: String, targetReps: Int, repsPerSet: Int = 20) {
        viewModelScope.launch {
            exerciseDao.insert(
                ExerciseEntity(name = name, targetReps = targetReps, repsPerSet = repsPerSet),
            )
        }
    }

    fun syncNow(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = if (syncManager.isSignedIn) {
                runCatching { syncManager.syncNow() }.isSuccess
            } else {
                false
            }
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }

    private suspend fun pushToCloud(workout: WorkoutEntity, exercises: List<ExerciseEntity>) {
        if (!syncManager.isSignedIn) return
        withContext(Dispatchers.IO) {
            runCatching { syncManager.pushWorkoutsToCloud(workout, exercises) }
        }
    }
}

class WorkoutViewModelFactory(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val syncManager: SyncManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
            return WorkoutViewModel(workoutDao, exerciseDao, syncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
