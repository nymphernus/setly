package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.ExerciseDao
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.WorkoutDao
import com.nmph.setly.utils.DistributionHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExerciseViewModel(
    private val dao: ExerciseDao,
    private val workoutDao: WorkoutDao,
) : ViewModel() {
    val exercises: StateFlow<List<ExerciseEntity>> = dao.getAllExercises()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Глобальное упражнение создаётся только с названием и размером подхода. */
    fun addExercise(name: String, repsPerSet: Int) {
        viewModelScope.launch {
            dao.insert(
                ExerciseEntity(
                    name = name,
                    targetReps = 0,
                    repsPerSet = repsPerSet.coerceAtLeast(1),
                ),
            )
        }
    }

    fun updateExercise(exercise: ExerciseEntity) {
        viewModelScope.launch { dao.update(exercise) }
    }

    /** Удаляем упражнение и все ссылки на него из тренировок. */
    fun deleteExercise(exercise: ExerciseEntity) {
        viewModelScope.launch {
            workoutDao.deleteExerciseLinks(exercise.id)
            dao.delete(exercise)
        }
    }

    fun getExerciseById(id: Int): ExerciseEntity? = exercises.value.find { it.id == id }

    companion object {
        val DEFAULT_REPS_PER_SET = DistributionHelper.DEFAULT_REPS_PER_SET
    }
}

class ExerciseViewModelFactory(
    private val dao: ExerciseDao,
    private val workoutDao: WorkoutDao,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExerciseViewModel::class.java)) {
            return ExerciseViewModel(dao, workoutDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
