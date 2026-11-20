package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.ProgressDao
import com.nmph.setly.data.local.WorkoutDao
import com.nmph.setly.data.local.WorkoutWithExercises
import com.nmph.setly.data.sync.SyncManager
import com.nmph.setly.utils.DistributionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@kotlinx.coroutines.ExperimentalCoroutinesApi
class HistoryViewModel(
    private val workoutDao: WorkoutDao,
    private val progressDao: ProgressDao,
    private val syncManager: SyncManager,
) : ViewModel() {
    private val _month = kotlinx.coroutines.flow.MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    init {
        // Первое значение эмитируется сразу, дальше — при каждом переключении месяца.
        viewModelScope.launch {
            _month.collect { yearMonth ->
                if (syncManager.isSignedIn) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            syncManager.pullProgressFromCloud(
                                yearMonth.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                                yearMonth.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE),
                            )
                        }
                    }
                }
            }
        }
    }

    val dayProgress: StateFlow<Map<LocalDate, Float>> = _month
        .flatMapLatest { yearMonth ->
            val start = yearMonth.atDay(1)
            val end = yearMonth.atEndOfMonth()
            val periodFlow: Flow<List<com.nmph.setly.data.local.ProgressEntity>> =
                progressDao.getProgressForPeriod(
                    start.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    end.format(DateTimeFormatter.ISO_LOCAL_DATE),
                )
            combine(workoutDao.getAllWorkouts(), periodFlow) { workouts, progress ->
                computeMonthProgress(yearMonth, workouts, progress)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    fun prevMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    private fun computeMonthProgress(
        yearMonth: YearMonth,
        workouts: List<WorkoutWithExercises>,
        progress: List<com.nmph.setly.data.local.ProgressEntity>,
    ): Map<LocalDate, Float> {
        val result = mutableMapOf<LocalDate, Float>()
        for (day in 1..yearMonth.lengthOfMonth()) {
            val date = yearMonth.atDay(day)
            val active = workouts.filter { it.isActiveOn(date) }
            if (active.isEmpty()) continue

            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val activeExerciseIds = active.flatMap { it.exercises.map { exercise -> exercise.id } }.toSet()

            // Прогресс считаем в подходах — так календарь соответствует экрану тренировки.
            val totalSets = active.sumOf { workout ->
                workout.exercises.sumOf { exercise ->
                    val daily = DistributionHelper.dailyReps(workout.workout, exercise)
                    DistributionHelper.getSetsForExercise(exercise, daily).size
                }
            }
            if (totalSets <= 0) continue
            val doneSets = progress
                .filter { it.date == dateString && it.exerciseId in activeExerciseIds }
                .sumOf { it.completedSets.size }
            result[date] = (doneSets.toFloat() / totalSets).coerceIn(0f, 1f)
        }
        return result
    }

    private fun WorkoutWithExercises.isActiveOn(date: LocalDate): Boolean {
        val start = Instant.ofEpochMilli(workout.startDate).atZone(ZoneId.systemDefault()).toLocalDate()
        return !date.isBefore(start) && !date.isAfter(start.plusDays(workout.periodDays.toLong() - 1))
    }
}

class HistoryViewModelFactory(
    private val workoutDao: WorkoutDao,
    private val progressDao: ProgressDao,
    private val syncManager: SyncManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            return HistoryViewModel(workoutDao, progressDao, syncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
