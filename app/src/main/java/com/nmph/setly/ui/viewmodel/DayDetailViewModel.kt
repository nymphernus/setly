package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.ProgressDao
import com.nmph.setly.data.local.WorkoutDao
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.data.local.withPlans
import com.nmph.setly.utils.DistributionHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayExerciseItem(
    val exercise: ExerciseEntity,
    val dailyTargetReps: Int,
    val repsPerSet: Int,
    val sets: List<Int>,
    val completedIndexes: List<Int>,
) {
    val completedReps: Int get() = completedIndexes.sumOf { sets.getOrNull(it) ?: 0 }
}

data class DayWorkoutItem(
    val workout: WorkoutEntity,
    val exercises: List<DayExerciseItem>,
)

/** Данные детализации одного дня календаря. */
class DayDetailViewModel(
    date: String,
    workoutDao: WorkoutDao,
    progressDao: ProgressDao,
) : ViewModel() {
    val items: StateFlow<List<DayWorkoutItem>> = combine(
        workoutDao.getAllWorkouts(),
        workoutDao.observePlannedExercises(),
        progressDao.getProgressForDate(date),
    ) { workoutEntities, rows, progress ->
        val day = runCatching { LocalDate.parse(date) }.getOrNull()
        if (day == null) {
            emptyList()
        } else {
            workoutEntities.withPlans(rows)
                .filter { isActiveOn(it.workout, day) }
                .map { plan ->
                    DayWorkoutItem(
                        workout = plan.workout,
                        exercises = plan.exercises.map { planned ->
                            val sets = DistributionHelper.setsForPlan(planned, plan.workout)
                            val saved = progress
                                .firstOrNull { it.exerciseId == planned.exercise.id }
                                ?.completedSets
                                .orEmpty()
                            DayExerciseItem(
                                exercise = planned.exercise,
                                dailyTargetReps = planned.dailyTargetReps,
                                repsPerSet = planned.repsPerSet,
                                sets = sets,
                                completedIndexes = saved.filter { it in sets.indices }.distinct(),
                            )
                        },
                    )
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun isActiveOn(workout: WorkoutEntity, date: LocalDate): Boolean {
        val start = Instant.ofEpochMilli(workout.startDate).atZone(ZoneId.systemDefault()).toLocalDate()
        return !date.isBefore(start) && !date.isAfter(start.plusDays(workout.periodDays.toLong() - 1))
    }
}

class DayDetailViewModelFactory(
    private val date: String,
    private val workoutDao: WorkoutDao,
    private val progressDao: ProgressDao,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DayDetailViewModel::class.java)) {
            return DayDetailViewModel(date, workoutDao, progressDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
