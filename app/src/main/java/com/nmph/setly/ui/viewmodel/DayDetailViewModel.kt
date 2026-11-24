package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.AchievementDao
import com.nmph.setly.data.local.ExerciseDao
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.ProgressDao
import com.nmph.setly.data.local.ProgressEntity
import com.nmph.setly.data.local.WorkoutDao
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.data.local.WorkoutPlan
import com.nmph.setly.data.local.withPlans
import com.nmph.setly.data.sync.SyncManager
import com.nmph.setly.utils.AchievementChecker
import com.nmph.setly.utils.AchievementContext
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

data class DayExerciseItem(
    val exercise: ExerciseEntity,
    val dailyTargetReps: Int,
    val adjustedTargetReps: Int,
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
    private val date: String,
    private val workoutDao: WorkoutDao,
    private val progressDao: ProgressDao,
    private val exerciseDao: ExerciseDao,
    private val syncManager: SyncManager,
    private val achievementDao: AchievementDao,
) : ViewModel() {
    val items: StateFlow<List<DayWorkoutItem>> = combine(
        workoutDao.getAllWorkouts(),
        workoutDao.observePlannedExercises(),
        progressDao.getProgressForDate(date),
        progressDao.observeAllProgress(),
    ) { workoutEntities, rows, progress, allProgress ->
        val day = runCatching { LocalDate.parse(date) }.getOrNull()
        if (day == null) {
            emptyList()
        } else {
            workoutEntities.withPlans(rows)
                .filter { plan ->
                    val active = isActiveOn(plan.workout, day)
                    val hasRecords = plan.exercises.any { planned ->
                        progress.any { record ->
                            record.date == date && record.exerciseId == planned.exercise.id
                        }
                    }
                    active || hasRecords
                }
                .map { plan ->
                    DayWorkoutItem(
                        workout = plan.workout,
                        exercises = plan.exercises.map { planned ->
                            val adjustedTarget = DistributionHelper.calculateAdjustedTarget(
                                planned, day, allProgress, plan.workout,
                            )
                            val sets = DistributionHelper.setsForAdjustedTarget(planned, adjustedTarget)
                            val saved = progress
                                .firstOrNull { it.exerciseId == planned.exercise.id }
                                ?.completedSets
                                .orEmpty()
                            DayExerciseItem(
                                exercise = planned.exercise,
                                dailyTargetReps = planned.dailyTargetReps,
                                adjustedTargetReps = adjustedTarget,
                                repsPerSet = planned.repsPerSet,
                                sets = sets,
                                completedIndexes = saved.filter { it in sets.indices }.distinct(),
                            )
                        },
                    )
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Добавляет выбранную тренировку в день: создаются пустые записи прогресса. */
    fun fillDay(plan: WorkoutPlan, onResult: (String) -> Unit) {
        viewModelScope.launch {
            var created = 0
            plan.exercises.forEach { planned ->
                val id = "${date}_${planned.exercise.id}"
                if (progressDao.getProgressById(id) == null) {
                    progressDao.insertOrUpdate(
                        ProgressEntity(
                            id = id,
                            date = date,
                            exerciseId = planned.exercise.id,
                            completedSets = emptyList(),
                            isCompleted = false,
                        ),
                    )
                    created++
                }
            }
            onResult(
                if (created > 0) "День заполнен: ${plan.workout.name}"
                else "Упражнения уже есть в этом дне",
            )
        }
    }

    /**
     * Сохраняет фактические повторения за день. Поскольку ProgressEntity хранит индексы
     * подходов, число повторений превращается в набор отмеченных подходов.
     */
    fun saveActualReps(
        workoutId: Int,
        actualReps: Map<Int, Int>,
        onResult: (String) -> Unit,
    ) {
        val workout = items.value.firstOrNull { it.workout.id == workoutId } ?: return
        val updates = workout.exercises.mapNotNull { exercise ->
            val actual = actualReps[exercise.exercise.id] ?: return@mapNotNull null
            val indexes = indexesForReps(exercise.sets, actual)
            ProgressEntity(
                id = "${date}_${exercise.exercise.id}",
                date = date,
                exerciseId = exercise.exercise.id,
                completedSets = indexes,
                isCompleted = exercise.sets.isNotEmpty() && indexes.size == exercise.sets.size,
            )
        }
        if (updates.isEmpty()) return
        viewModelScope.launch {
            updates.forEach { progress ->
                progressDao.insertOrUpdate(progress)
                if (syncManager.isSignedIn) {
                    withContext(Dispatchers.IO) {
                        runCatching { syncManager.pushProgressToCloud(progress) }
                    }
                }
            }
            AchievementChecker.checkAfterProgress(
                AchievementContext(
                    achievementDao = achievementDao,
                    workoutDao = workoutDao,
                    exerciseDao = exerciseDao,
                    progressDao = progressDao,
                ),
            )
            onResult("Фактические данные сохранены: ${workout.workout.name}")
        }
    }

    /** Заполняет подходы так, чтобы их сумма была максимально близка к введённому числу. */
    private fun indexesForReps(sets: List<Int>, actual: Int): List<Int> {
        if (actual <= 0 || sets.isEmpty()) return emptyList()
        var remaining = actual
        val indexes = mutableListOf<Int>()
        sets.forEachIndexed { index, reps ->
            if (remaining >= reps) {
                indexes += index
                remaining -= reps
            }
        }
        if (remaining > 0) {
            sets.indices
                .filterNot { it in indexes }
                .minByOrNull { kotlin.math.abs(sets[it] - remaining) }
                ?.let { indexes += it }
        }
        return indexes.sorted()
    }

    private fun isActiveOn(workout: WorkoutEntity, date: LocalDate): Boolean {
        val start = Instant.ofEpochMilli(workout.startDate).atZone(ZoneId.systemDefault()).toLocalDate()
        return !date.isBefore(start) && !date.isAfter(start.plusDays(workout.periodDays.toLong() - 1))
    }
}

class DayDetailViewModelFactory(
    private val date: String,
    private val workoutDao: WorkoutDao,
    private val progressDao: ProgressDao,
    private val exerciseDao: ExerciseDao,
    private val syncManager: SyncManager,
    private val achievementDao: AchievementDao,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DayDetailViewModel::class.java)) {
            return DayDetailViewModel(
                date, workoutDao, progressDao, exerciseDao, syncManager, achievementDao,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
