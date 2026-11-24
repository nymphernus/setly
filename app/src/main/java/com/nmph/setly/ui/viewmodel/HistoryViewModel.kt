package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.PlannedExerciseRow
import com.nmph.setly.data.local.ProgressDao
import com.nmph.setly.data.local.WorkoutDao
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.data.local.WorkoutPlan
import com.nmph.setly.data.local.withPlans
import com.nmph.setly.data.sync.SyncManager
import com.nmph.setly.utils.DistributionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val workoutDao: WorkoutDao,
    private val progressDao: ProgressDao,
    private val syncManager: SyncManager,
) : ViewModel() {
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    init {
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
            combine(
                workoutDao.getAllWorkouts(),
                workoutDao.observePlannedExercises(),
                periodFlow,
            ) { workoutEntities: List<WorkoutEntity>,
                rows: List<PlannedExerciseRow>,
                progress: List<com.nmph.setly.data.local.ProgressEntity> ->
                computeMonthProgress(yearMonth, workoutEntities.withPlans(rows), progress)
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
        plans: List<WorkoutPlan>,
        progress: List<com.nmph.setly.data.local.ProgressEntity>,
    ): Map<LocalDate, Float> {
        val result = mutableMapOf<LocalDate, Float>()
        for (day in 1..yearMonth.lengthOfMonth()) {
            val date = yearMonth.atDay(day)
            val active = plans.filter { isActiveOn(it.workout, date) }
            if (active.isEmpty()) continue

            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val activeExerciseIds = active
                .flatMap { plan -> plan.exercises.map { it.exercise.id } }
                .toSet()

            val totalSets = active.sumOf { plan ->
                plan.exercises.sumOf { DistributionHelper.setsForPlan(it, plan.workout).size }
            }
            if (totalSets <= 0) continue
            val doneSets = progress
                .filter { it.date == dateString && it.exerciseId in activeExerciseIds }
                .sumOf { it.completedSets.size }
            result[date] = (doneSets.toFloat() / totalSets).coerceIn(0f, 1f)
        }
        return result
    }

    private fun isActiveOn(workout: WorkoutEntity, date: LocalDate): Boolean {
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
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            return HistoryViewModel(workoutDao, progressDao, syncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
