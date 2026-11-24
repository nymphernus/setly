package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.AchievementDao
import com.nmph.setly.data.local.ExerciseDao
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.PlannedExercise
import com.nmph.setly.data.local.PlannedExerciseRow
import com.nmph.setly.data.local.ProgressDao
import com.nmph.setly.data.local.WorkoutDao
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.data.local.WorkoutExerciseCrossRef
import com.nmph.setly.data.local.WorkoutExerciseSettings
import com.nmph.setly.data.local.WorkoutPlan
import com.nmph.setly.data.local.withPlans
import com.nmph.setly.data.sync.SyncManager
import com.nmph.setly.utils.AchievementChecker
import com.nmph.setly.utils.AchievementContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkoutViewModel(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val progressDao: ProgressDao,
    private val syncManager: SyncManager,
    private val achievementDao: AchievementDao,
) : ViewModel() {
    val workouts: StateFlow<List<WorkoutPlan>> = combine(
        workoutDao.getAllWorkouts(),
        workoutDao.observePlannedExercises(),
    ) { workoutEntities, rows -> workoutEntities.withPlans(rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exercises: StateFlow<List<ExerciseEntity>> = exerciseDao.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveWorkout(
        id: Int,
        name: String,
        periodDays: Int,
        sleepStart: String,
        sleepEnd: String,
        notificationsEnabled: Boolean,
        remindBeforeMinutes: Int,
        settings: List<WorkoutExerciseSettings>,
        onResult: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            val existingPlan = workouts.value.firstOrNull { it.workout.id == id }
            val existing = existingPlan?.workout
            val isNew = existing == null
            val changedFields = mutableSetOf<String>()
            if (existing != null) {
                if (existing.name != name) changedFields += "name"
                if (existing.sleepStart != sleepStart || existing.sleepEnd != sleepEnd) {
                    changedFields += "sleep"
                }
                val oldReps = existingPlan.exercises
                    .associate { it.exercise.id to it.repsPerSet }
                if (settings.any { oldReps[it.exerciseId] != it.repsPerSet }) {
                    changedFields += "reps"
                }
            }
            val workout = WorkoutEntity(
                id = existing?.id ?: 0,
                name = name,
                startDate = existing?.startDate ?: System.currentTimeMillis(),
                periodDays = periodDays,
                sleepStart = sleepStart,
                sleepEnd = sleepEnd,
                notificationsEnabled = notificationsEnabled,
                remindBeforeMinutes = remindBeforeMinutes.coerceAtLeast(1),
            )
            val links = settings.map { item ->
                WorkoutExerciseCrossRef(
                    workoutId = 0,
                    exerciseId = item.exerciseId,
                    dailyTargetReps = item.dailyTargetReps.coerceAtLeast(0),
                    repsPerSet = item.repsPerSet.coerceAtLeast(1),
                )
            }
            // Сначала сохраняем локально (offline-first), затем бэкапим в облако.
            val savedId = workoutDao.saveWorkout(workout, links)
            val savedPlan = WorkoutPlan(
                workout = workout.copy(id = savedId),
                exercises = links.mapNotNull { link ->
                    exerciseDao.getExerciseById(link.exerciseId)?.let { exercise ->
                        PlannedExercise(exercise, link.dailyTargetReps, link.repsPerSet)
                    }
                },
            )
            AchievementChecker.checkAfterWorkoutSave(
                AchievementContext(
                    achievementDao = achievementDao,
                    workoutDao = workoutDao,
                    exerciseDao = exerciseDao,
                    progressDao = progressDao,
                    changedFields = changedFields,
                    hour = -1, // time-трофеи проверяем только при сохранении подхода
                ),
            )
            onResult(if (isNew) "Тренировка создана" else "Тренировка обновлена")
            pushToCloud(savedPlan)
        }
    }

    fun deleteWorkout(workout: WorkoutEntity, onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            workoutDao.delete(workout)
            onResult("Тренировка удалена")
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (syncManager.isSignedIn) {
                runCatching { syncManager.deleteWorkoutFromCloud(workout.id) }
            }
        }
    }

    fun syncNow(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = if (syncManager.isSignedIn) {
                runCatching { syncManager.syncNow() }.getOrDefault(false)
            } else {
                false
            }
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }

    private suspend fun pushToCloud(plan: WorkoutPlan) {
        if (!syncManager.isSignedIn) return
        withContext(Dispatchers.IO) {
            runCatching { syncManager.pushWorkoutToCloud(plan) }
        }
    }
}

class WorkoutViewModelFactory(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val progressDao: ProgressDao,
    private val syncManager: SyncManager,
    private val achievementDao: AchievementDao,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
            return WorkoutViewModel(
                workoutDao, exerciseDao, progressDao, syncManager, achievementDao,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
