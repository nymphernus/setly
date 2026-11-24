package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.ProgressDao
import com.nmph.setly.data.local.WorkoutDao
import com.nmph.setly.data.local.withPlans
import com.nmph.setly.utils.DistributionHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Сумма выполненных повторений по одному упражнению за весь период тренировки. */
data class ExerciseTotal(
    val exerciseId: Int,
    val completedReps: Int,
    val completedSets: Int,
)

data class WorkoutTotals(
    val byExercise: Map<Int, ExerciseTotal> = emptyMap(),
) {
    val completedReps: Int get() = byExercise.values.sumOf { it.completedReps }
    val completedSets: Int get() = byExercise.values.sumOf { it.completedSets }
}

/** Считает «Выполнено за всё время» по всем дням периода тренировки. */
class WorkoutDetailViewModel(
    workoutId: Int,
    workoutDao: WorkoutDao,
    progressDao: ProgressDao,
) : ViewModel() {
    val totals: StateFlow<WorkoutTotals> = combine(
        workoutDao.getAllWorkouts(),
        workoutDao.observePlannedExercises(),
        progressDao.observeAllProgress(),
    ) { workoutEntities, rows, progress ->
        val plan = workoutEntities.withPlans(rows).firstOrNull { it.workout.id == workoutId }
        if (plan == null) {
            WorkoutTotals()
        } else {
            val start = Instant.ofEpochMilli(plan.workout.startDate)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val end = start.plusDays(plan.workout.periodDays.toLong() - 1)

            val byExercise = plan.exercises.associate { planned ->
                val sets = DistributionHelper.setsForPlan(planned, plan.workout)
                val entries = progress.filter { record ->
                    val date = runCatching { LocalDate.parse(record.date) }.getOrNull()
                    record.exerciseId == planned.exercise.id &&
                        date != null && !date.isBefore(start) && !date.isAfter(end)
                }
                val total = ExerciseTotal(
                    exerciseId = planned.exercise.id,
                    completedReps = entries.sumOf { record ->
                        record.completedSets.sumOf { index -> sets.getOrNull(index) ?: 0 }
                    },
                    completedSets = entries.sumOf { record ->
                        record.completedSets.count { index -> index in sets.indices }
                    },
                )
                planned.exercise.id to total
            }
            WorkoutTotals(byExercise)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutTotals())

    /** Нормы на сегодня с учётом долгов — используются в подсказках карточек упражнений. */
    val adjustedTargets: StateFlow<Map<Int, Int>> = combine(
        workoutDao.getAllWorkouts(),
        workoutDao.observePlannedExercises(),
        progressDao.observeAllProgress(),
    ) { workoutEntities, rows, progress ->
        val plan = workoutEntities.withPlans(rows).firstOrNull { it.workout.id == workoutId }
            ?: return@combine emptyMap()
        plan.exercises.associate { planned ->
            planned.exercise.id to DistributionHelper.calculateAdjustedTarget(
                planned, LocalDate.now(), progress, plan.workout,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

class WorkoutDetailViewModelFactory(
    private val workoutId: Int,
    private val workoutDao: WorkoutDao,
    private val progressDao: ProgressDao,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkoutDetailViewModel::class.java)) {
            return WorkoutDetailViewModel(workoutId, workoutDao, progressDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
