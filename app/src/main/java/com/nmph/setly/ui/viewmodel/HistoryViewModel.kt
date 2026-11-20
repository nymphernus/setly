package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.ProgressDao
import com.nmph.setly.data.local.WorkoutDao
import com.nmph.setly.data.local.WorkoutWithExercises
import com.nmph.setly.utils.DistributionHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@kotlinx.coroutines.ExperimentalCoroutinesApi
class HistoryViewModel(
    private val workoutDao: WorkoutDao,
    private val progressDao: ProgressDao,
) : ViewModel() {
    private val _month = kotlinx.coroutines.flow.MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

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

            // Знаменатель — дневные цели, иначе сырой targetReps за весь период не даст 100%.
            val targets = active.sumOf { workout ->
                DistributionHelper.calculateTargetsForWorkout(workout.workout, workout.exercises, date, progress)
                    .values.sum()
            }
            if (targets <= 0) continue
            val done = progress.filter { it.date == date.format(DateTimeFormatter.ISO_LOCAL_DATE) }
                .sumOf { it.completedReps }
            result[date] = (done.toFloat() / targets).coerceIn(0f, 1f)
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
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            return HistoryViewModel(workoutDao, progressDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
