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
    val completedSets: List<Int>,
) {
    val completedToday: Int get() = completedSets.sum()
}

data class TodayWorkoutItem(
    val workout: WorkoutEntity,
    val exercises: List<TodayExerciseItem>,
)

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
            val targets = DistributionHelper.calculateTargetsForWorkout(
                workout.workout, workout.exercises, today, progressList,
            )
            TodayWorkoutItem(
                workout = workout.workout,
                exercises = workout.exercises.map { exercise ->
                    val target = targets[exercise.id] ?: 0
                    TodayExerciseItem(
                        exercise = exercise,
                        targetForToday = target,
                        sets = DistributionHelper.splitIntoSets(
                            target,
                            exercise.repsPerSet.coerceAtLeast(1),
                        ),
                        completedSets = progressList.firstOrNull { it.exerciseId == exercise.id }?.completedSets
                            ?: emptyList(),
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun getTodayDate(): String = todayDate

    // Чекбокс подхода: добавляем/убираем элемент в позиции setIndex.
    fun toggleSet(exerciseId: Int, setIndex: Int) {
        val item = todayWorkouts.value
            .flatMap { it.exercises }
            .firstOrNull { it.exercise.id == exerciseId } ?: return
        val reps = item.sets.getOrNull(setIndex) ?: return
        val current = progress.value.firstOrNull { it.exerciseId == exerciseId }?.completedSets ?: emptyList()

        val updated = if (current.getOrNull(setIndex) == reps) {
            current.filterIndexed { index, _ -> index != setIndex }
        } else {
            current.toMutableList().apply {
                // Если отметили позже первый подход, предыдущие считаем выполненными.
                while (size < setIndex) add(item.sets[size])
                if (size == setIndex) add(reps) else this[setIndex] = reps
            }.toList()
        }

        val updatedProgress = ProgressEntity(
            id = "${todayDate}_$exerciseId",
            date = todayDate,
            exerciseId = exerciseId,
            completedSets = updated,
            isCompleted = updated == item.sets,
        )
        viewModelScope.launch {
            // Сначала Room, затем бэкап в облако в фоновом потоке.
            progressDao.insertOrUpdate(updatedProgress)
            if (syncManager.isSignedIn) {
                withContext(Dispatchers.IO) {
                    runCatching { syncManager.pushProgressToCloud(updatedProgress) }
                }
            }
        }
    }

    // Завершение тренировки: все подходы на сегодня отмечаются выполненными.
    fun completeWorkout(workoutId: Int, onComplete: () -> Unit) {
        val todayItem = todayWorkouts.value.firstOrNull { it.workout.id == workoutId } ?: return
        val records = todayItem.exercises.map { item ->
            ProgressEntity(
                id = "${todayDate}_${item.exercise.id}",
                date = todayDate,
                exerciseId = item.exercise.id,
                completedSets = item.sets,
                isCompleted = true,
            )
        }
        viewModelScope.launch {
            records.forEach { progressDao.insertOrUpdate(it) }
            if (syncManager.isSignedIn) {
                withContext(Dispatchers.IO) {
                    records.forEach { record ->
                        runCatching { syncManager.pushProgressToCloud(record) }
                    }
                }
            }
            onComplete()
        }
    }

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
