package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.ExerciseDao
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.ProgressDao
import com.nmph.setly.data.local.ProgressEntity
import com.nmph.setly.utils.DistributionHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TodayItem(
    val exercise: ExerciseEntity,
    val targetForToday: Int,
    val completedToday: Int,
)

class TodayViewModel(
    private val exerciseDao: ExerciseDao,
    private val progressDao: ProgressDao,
) : ViewModel() {
    private val today: LocalDate = LocalDate.now()
    private val todayDate: String = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

    private val exercises: StateFlow<List<ExerciseEntity>> = exerciseDao.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val progress: StateFlow<List<ProgressEntity>> = progressDao.getProgressForDate(todayDate)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayItems: StateFlow<List<TodayItem>> = combine(exercises, progress) { allExercises, progressList ->
        allExercises.map { exercise ->
            TodayItem(
                exercise = exercise,
                targetForToday = DistributionHelper.calculateTargetForToday(exercise, today, progressList),
                completedToday = progressList.firstOrNull { it.exerciseId == exercise.id }?.completedReps ?: 0,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun getTodayDate(): String = todayDate

    fun updateProgress(exerciseId: Int, completedReps: Int) {
        val exercise = exercises.value.find { it.id == exerciseId } ?: return
        val safeReps = completedReps.coerceAtLeast(0)
        val target = DistributionHelper.calculateTargetForToday(exercise, today, progress.value)
        viewModelScope.launch {
            progressDao.insertOrUpdate(
                ProgressEntity(
                    id = "${todayDate}_$exerciseId",
                    date = todayDate,
                    exerciseId = exerciseId,
                    completedReps = safeReps,
                    isCompleted = safeReps >= target,
                ),
            )
        }
    }
}

class TodayViewModelFactory(
    private val exerciseDao: ExerciseDao,
    private val progressDao: ProgressDao,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodayViewModel::class.java)) {
            return TodayViewModel(exerciseDao, progressDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
