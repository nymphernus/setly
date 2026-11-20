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
import com.nmph.setly.data.sync.SyncManager
import com.nmph.setly.utils.DistributionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TodayExerciseItem(
    val exercise: ExerciseEntity,
    val targetForToday: Int,
    val sets: List<Int>,
    /** Индексы отмеченных подходов (не значения повторений!). */
    val completedIndexes: List<Int>,
) {
    val completedToday: Int get() = completedIndexes.sumOf { sets.getOrNull(it) ?: 0 }
    val completedSetsCount: Int get() = completedIndexes.count { it in sets.indices }
    val isDone: Boolean get() = sets.isNotEmpty() && completedSetsCount == sets.size
}

data class TodayWorkoutItem(
    val workout: WorkoutEntity,
    val exercises: List<TodayExerciseItem>,
) {
    val totalSets: Int get() = exercises.sumOf { it.sets.size }
    val completedSets: Int get() = exercises.sumOf { it.completedSetsCount }
    val isDone: Boolean get() = totalSets > 0 && completedSets == totalSets
}

class TodayViewModel(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val progressDao: ProgressDao,
    private val syncManager: SyncManager,
) : ViewModel() {
    private val today: LocalDate = LocalDate.now()
    private val todayDate: String = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

    private val workouts: StateFlow<List<WorkoutWithExercises>> = workoutDao.getAllWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val progress: StateFlow<List<ProgressEntity>> = progressDao.getProgressForDate(todayDate)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayWorkouts: StateFlow<List<TodayWorkoutItem>> = combine(workouts, progress) { allWorkouts, progressList ->
        allWorkouts.filter { it.isActiveOn(today) }.map { workout ->
            val targets = DistributionHelper.calculateTargetsForWorkout(workout.workout, workout.exercises)
            TodayWorkoutItem(
                workout = workout.workout,
                exercises = workout.exercises.map { exercise ->
                    val target = targets[exercise.id] ?: 0
                    val sets = DistributionHelper.getSetsForExercise(exercise, target)
                    val saved = progressList.firstOrNull { it.exerciseId == exercise.id }?.completedSets.orEmpty()
                    TodayExerciseItem(
                        exercise = exercise,
                        targetForToday = target,
                        sets = sets,
                        completedIndexes = saved.filter { it in sets.indices }.distinct(),
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun getTodayDate(): String = todayDate

    /**
     * Переключает один подход по индексу. Отметки независимы: можно снять любой
     * подход, не затрагивая остальные.
     */
    fun toggleSet(exerciseId: Int, setIndex: Int) {
        val item = todayWorkouts.value
            .flatMap { it.exercises }
            .firstOrNull { it.exercise.id == exerciseId } ?: return
        if (setIndex !in item.sets.indices) return

        val current = progress.value
            .firstOrNull { it.exerciseId == exerciseId }?.completedSets.orEmpty()
            .filter { it in item.sets.indices }
            .distinct()
        val updated = if (setIndex in current) {
            current - setIndex
        } else {
            (current + setIndex).sorted()
        }

        val updatedProgress = ProgressEntity(
            id = "${todayDate}_$exerciseId",
            date = todayDate,
            exerciseId = exerciseId,
            completedSets = updated,
            isCompleted = updated.size == item.sets.size,
        )
        viewModelScope.launch {
            progressDao.insertOrUpdate(updatedProgress)
            if (syncManager.isSignedIn) {
                withContext(Dispatchers.IO) {
                    runCatching { syncManager.pushProgressToCloud(updatedProgress) }
                }
            }
        }
    }

    fun isWorkoutComplete(workoutId: Int): Boolean =
        todayWorkouts.value.firstOrNull { it.workout.id == workoutId }?.isDone == true

    private fun WorkoutWithExercises.isActiveOn(date: LocalDate): Boolean {
        val start = Instant.ofEpochMilli(workout.startDate).atZone(ZoneId.systemDefault()).toLocalDate()
        return !date.isBefore(start) && !date.isAfter(start.plusDays(workout.periodDays.toLong() - 1))
    }
}

class TodayViewModelFactory(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val progressDao: ProgressDao,
    private val syncManager: SyncManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodayViewModel::class.java)) {
            return TodayViewModel(workoutDao, exerciseDao, progressDao, syncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
