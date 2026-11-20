package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.ExerciseDao
import com.nmph.setly.data.local.ExerciseEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExerciseViewModel(private val dao: ExerciseDao) : ViewModel() {
    val exercises: StateFlow<List<ExerciseEntity>> = dao.getAllExercises()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun addExercise(name: String, targetReps: Int, repsPerSet: Int = 20) {
        viewModelScope.launch {
            dao.insert(
                ExerciseEntity(name = name, targetReps = targetReps, repsPerSet = repsPerSet),
            )
        }
    }

    fun updateExercise(exercise: ExerciseEntity) {
        viewModelScope.launch { dao.update(exercise) }
    }

    fun deleteExercise(exercise: ExerciseEntity) {
        viewModelScope.launch { dao.delete(exercise) }
    }

    // Редактирование открывается по ID, а не целиком по объекту: список уже есть в StateFlow.
    fun getExerciseById(id: Int): ExerciseEntity? = exercises.value.find { it.id == id }
}

class ExerciseViewModelFactory(private val dao: ExerciseDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExerciseViewModel::class.java)) {
            return ExerciseViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
