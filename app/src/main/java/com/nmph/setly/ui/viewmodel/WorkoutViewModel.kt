package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.ExerciseDao
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.WorkoutDao
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.data.local.WorkoutWithExercises
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutViewModel(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
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
        exerciseIds: List<Int>,
    ) {
        viewModelScope.launch {
            val existing = if (id != -1) workoutDao.getWorkoutById(id)?.workout else null
            workoutDao.saveWorkout(
                WorkoutEntity(
                    id = existing?.id ?: 0,
                    name = name,
                    startDate = existing?.startDate ?: System.currentTimeMillis(),
                    periodDays = periodDays,
                    sleepStart = sleepStart,
                    sleepEnd = sleepEnd,
                ),
                exerciseIds,
            )
        }
    }

    fun deleteWorkout(workout: WorkoutEntity) {
        viewModelScope.launch { workoutDao.delete(workout) }
    }

    fun createExercise(name: String, targetReps: Int) {
        viewModelScope.launch { exerciseDao.insert(ExerciseEntity(name = name, targetReps = targetReps)) }
    }
}

class WorkoutViewModelFactory(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
            return WorkoutViewModel(workoutDao, exerciseDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
