package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.ExerciseDao
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.ProgressDao
import com.nmph.setly.data.local.ProgressEntity
import com.nmph.setly.data.local.WorkoutDao
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.data.local.WorkoutWithExercises
import com.nmph.setly.utils.DistributionHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TodayExerciseItem(
    val exercise: ExerciseEntity,
    val targetForToday: Int,
    val completedToday: Int,
)

data class TodayWorkoutItem(
    val workout: WorkoutEntity,
    val exercises: List<TodayExerciseItem>,
)

class TodayViewModel(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val progressDao: ProgressDao,
) : ViewModel() {
    private val today: LocalDate = LocalDate.now()
    private val todayDate: String = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

    private val workouts: StateFlow<List<WorkoutWithExercises>> = workoutDao.getAllWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val progress: StateFlow<List<ProgressEntity>> = progressDao.getProgressForDate(todayDate)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayWorkouts: StateFlow<List<TodayWorkoutItem>> = combine(workouts, progress) { allWorkouts, progressList ->
        allWorkouts.filter { it.isActiveOn(today) }.map { workout ->
            val targets = DistributionHelper.calculateTargetsForWorkout(
                workout.workout, workout.exercises, today, progressList,
            )
            TodayWorkoutItem(
                workout = workout.workout,
                exercises = workout.exercises.map { exercise ->
                    TodayExerciseItem(
                        exercise = exercise,
                        targetForToday = targets[exercise.id] ?: 0,
                        completedToday = progressList.firstOrNull { it.exerciseId == exercise.id }?.completedReps ?: 0,
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun getTodayDate(): String = todayDate

    fun updateProgress(exerciseId: Int, completedReps: Int) {
        val safeReps = completedReps.coerceAtLeast(0)
        // Цель нужна только для isCompleted; пересчитываем по первой активной тренировке упражнения.
        val target = todayWorkouts.value
            .flatMap { it.exercises }
            .firstOrNull { it.exercise.id == exerciseId }?.targetForToday ?: safeReps
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

    private fun WorkoutWithExercises.isActiveOn(date: LocalDate): Boolean {
        val start = java.time.Instant.ofEpochMilli(workout.startDate)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        return !date.isBefore(start) && !date.isAfter(start.plusDays(workout.periodDays.toLong() - 1))
    }
}

class TodayViewModelFactory(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val progressDao: ProgressDao,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodayViewModel::class.java)) {
            return TodayViewModel(workoutDao, exerciseDao, progressDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
