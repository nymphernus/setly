package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.AchievementDao
import com.nmph.setly.data.local.ExerciseDao
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.PlannedExerciseRow
import com.nmph.setly.data.local.ProgressDao
import com.nmph.setly.data.local.ProgressEntity
import com.nmph.setly.data.local.WorkoutDao
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.data.local.withPlans
import com.nmph.setly.data.sync.SyncManager
import com.nmph.setly.utils.AchievementChecker
import com.nmph.setly.utils.AchievementContext
import com.nmph.setly.utils.DistributionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    val dailyTargetReps: Int,
    /** Норма на сегодня с учётом долгов за предыдущие 7 дней. */
    val adjustedTargetReps: Int,
    val repsPerSet: Int,
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

/** Фаза таймера: синее выполнение подхода или оранжевый отдых. */
enum class TimerPhase { EXECUTION, REST }

data class WorkoutTimerState(
    val isActive: Boolean = false,
    val phase: TimerPhase = TimerPhase.EXECUTION,
    val timeLeft: Int = 0,
    val totalSeconds: Int = 0,
    val exerciseId: Int? = null,
)

class TodayViewModel(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val progressDao: ProgressDao,
    private val syncManager: SyncManager,
    private val achievementDao: AchievementDao,
) : ViewModel() {
    private val today: LocalDate = LocalDate.now()
    private val todayDate: String = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

    private val workouts = workoutDao.getAllWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val plannedExercises = workoutDao.observePlannedExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val progress: StateFlow<List<ProgressEntity>> = progressDao.getProgressForDate(todayDate)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val allProgress: StateFlow<List<ProgressEntity>> = progressDao.observeAllProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _timer = MutableStateFlow(WorkoutTimerState())
    val timer: StateFlow<WorkoutTimerState> = _timer
    private var timerJob: Job? = null

    val todayWorkouts: StateFlow<List<TodayWorkoutItem>> = combine(
        workouts,
        plannedExercises,
        progress,
        allProgress,
    ) { workoutEntities: List<WorkoutEntity>,
        rows: List<PlannedExerciseRow>,
        todayProgressList: List<ProgressEntity>,
        allProgressList: List<ProgressEntity> ->
        workoutEntities.withPlans(rows)
            .filter { isActiveOn(it.workout, today) }
            .map { plan ->
                TodayWorkoutItem(
                    workout = plan.workout,
                    exercises = plan.exercises.map { planned ->
                        val adjustedTarget = DistributionHelper.calculateAdjustedTarget(
                            planned, today, allProgressList, plan.workout,
                        )
                        val sets = DistributionHelper.setsForAdjustedTarget(planned, adjustedTarget)
                        val saved = todayProgressList
                            .firstOrNull { it.exerciseId == planned.exercise.id }
                            ?.completedSets
                            .orEmpty()
                        TodayExerciseItem(
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun getTodayDate(): String = todayDate

    /**
     * Запускает «Выполнение» длительностью estimatedTimePerSet, затем автоматически
     * «Отдых» из рассчитанного restMinutes. Повторное нажатие чекбокса снимает таймер.
     */
    fun startSetTimer(exerciseId: Int, reps: Int, unit: String, restMinutes: Int) {
        cancelTimer()
        val executionSeconds = DistributionHelper.estimatedTimePerSet(reps, unit) * 60
        val restSeconds = restMinutes.coerceAtLeast(0) * 60
        if (executionSeconds <= 0 && restSeconds <= 0) return
        timerJob = viewModelScope.launch {
            if (executionSeconds > 0) {
                countdown(executionSeconds, TimerPhase.EXECUTION, exerciseId)
            }
            if (restSeconds > 0) {
                countdown(restSeconds, TimerPhase.REST, exerciseId)
            }
            _timer.value = WorkoutTimerState()
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _timer.value = WorkoutTimerState()
    }

    private suspend fun countdown(totalSeconds: Int, phase: TimerPhase, exerciseId: Int) {
        _timer.value = WorkoutTimerState(
            isActive = true,
            phase = phase,
            timeLeft = totalSeconds,
            totalSeconds = totalSeconds,
            exerciseId = exerciseId,
        )
        for (left in totalSeconds - 1 downTo 0) {
            delay(1_000)
            _timer.value = WorkoutTimerState(
                isActive = true,
                phase = phase,
                timeLeft = left,
                totalSeconds = totalSeconds,
                exerciseId = exerciseId,
            )
        }
    }

    /** Переключает один подход по индексу; остальные отметки не затрагиваются. */
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
            checkAchievements()
            if (syncManager.isSignedIn) {
                withContext(Dispatchers.IO) {
                    runCatching { syncManager.pushProgressToCloud(updatedProgress) }
                }
            }
        }
    }

    /**
     * Переключает сразу все подходы раунда (пары «упражнение → индекс подхода»).
     * Если все подходы раунда отмечены — снимает отметки, иначе отмечает все.
     */
    fun toggleRound(entries: List<Pair<Int, Int>>) {
        val items = todayWorkouts.value.flatMap { it.exercises }.associateBy { it.exercise.id }
        val valid = entries.filter { (exerciseId, index) ->
            items[exerciseId]?.sets?.indices?.contains(index) == true
        }
        if (valid.isEmpty()) return
        val allChecked = valid.all { (exerciseId, index) ->
            index in items.getValue(exerciseId).completedIndexes
        }
        val shouldCheck = !allChecked
        val updates = valid.groupBy({ it.first }, { it.second }).mapNotNull { (exerciseId, indexes) ->
            val item = items[exerciseId] ?: return@mapNotNull null
            val updated = if (shouldCheck) {
                item.completedIndexes.toSet() + indexes
            } else {
                item.completedIndexes.toSet() - indexes.toSet()
            }
            ProgressEntity(
                id = "${todayDate}_$exerciseId",
                date = todayDate,
                exerciseId = exerciseId,
                completedSets = updated.sorted(),
                isCompleted = updated.size == item.sets.size,
            )
        }
        if (updates.isEmpty()) return
        viewModelScope.launch {
            updates.forEach { updatedProgress ->
                progressDao.insertOrUpdate(updatedProgress)
                if (syncManager.isSignedIn) {
                    withContext(Dispatchers.IO) {
                        runCatching { syncManager.pushProgressToCloud(updatedProgress) }
                    }
                }
            }
            checkAchievements()
        }
    }

    private suspend fun checkAchievements() {
        AchievementChecker.checkAfterProgress(
            AchievementContext(
                achievementDao = achievementDao,
                workoutDao = workoutDao,
                exerciseDao = exerciseDao,
                progressDao = progressDao,
            ),
        )
    }

    override fun onCleared() {
        cancelTimer()
        super.onCleared()
    }

    fun isWorkoutComplete(workoutId: Int): Boolean =
        todayWorkouts.value.firstOrNull { it.workout.id == workoutId }?.isDone == true

    private fun isActiveOn(workout: WorkoutEntity, date: LocalDate): Boolean {
        val start = Instant.ofEpochMilli(workout.startDate).atZone(ZoneId.systemDefault()).toLocalDate()
        return !date.isBefore(start) && !date.isAfter(start.plusDays(workout.periodDays.toLong() - 1))
    }
}

class TodayViewModelFactory(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val progressDao: ProgressDao,
    private val syncManager: SyncManager,
    private val achievementDao: AchievementDao,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodayViewModel::class.java)) {
            return TodayViewModel(
                workoutDao, exerciseDao, progressDao, syncManager, achievementDao,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
