package com.nmph.setly.utils

import com.nmph.setly.data.AchievementRepository
import com.nmph.setly.data.local.AchievementDao
import com.nmph.setly.data.local.ExerciseDao
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.ProgressDao
import com.nmph.setly.data.local.ProgressEntity
import com.nmph.setly.data.local.WorkoutDao
import com.nmph.setly.data.local.WorkoutPlan
import com.nmph.setly.data.local.withPlans
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** События трофеев; replay нужен, чтобы уведомление не потерялось до появления UI. */
object AchievementEvents {
    private val _messages = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun publish(message: String) {
        _messages.tryEmit(message)
    }
}

/** Всё необходимое для проверки условий; создаётся вызывающей ViewModel. */
data class AchievementContext(
    val achievementDao: AchievementDao,
    val workoutDao: WorkoutDao,
    val exerciseDao: ExerciseDao,
    val progressDao: ProgressDao,
    /** Какие поля тренировки изменились: name / reps / availableTime. */
    val changedFields: Set<String> = emptySet(),
    val hour: Int = LocalDateTime.now().hour,
)

data class AchievementSnapshot(
    val workouts: List<WorkoutPlan>,
    val exercises: List<ExerciseEntity>,
    val progress: List<ProgressEntity>,
)

private data class DayStats(
    var reps: Int = 0,
    var sets: Int = 0,
    val categories: MutableSet<String> = mutableSetOf(),
)

/**
 * Проверяет условия 50 трофеев по текущему состоянию БД.
 * Вызывается после сохранения прогресса и после сохранения тренировки.
 */
object AchievementChecker {
    private val AllCategories = listOf("Грудь", "Спина", "Ноги", "Руки", "Пресс", "Кардио")

    suspend fun checkAfterProgress(context: AchievementContext) {
        checkAll(context, snapshot(context))
    }

    suspend fun checkAfterWorkoutSave(context: AchievementContext) {
        checkAll(context, snapshot(context))
    }

    suspend fun checkStreak(context: AchievementContext, data: AchievementSnapshot) {
        val longest = longestStreak(data)
        listOf(
            2 to "streak_2",
            3 to "streak_3",
            7 to "streak_7",
            14 to "streak_14",
            30 to "streak_30",
            100 to "streak_100",
        ).forEach { (days, id) ->
            if (longest >= days) unlock(context, id)
        }
        if (hasReturn(data)) unlock(context, "return")

        val month = YearMonth.now()
        val thisMonthDays = trainingDays(data).count {
            it.year == month.year && it.month == month.month
        }
        if (thisMonthDays >= 10) unlock(context, "marathon")
    }

    suspend fun checkTotalReps(context: AchievementContext, data: AchievementSnapshot) {
        val (_, totalReps) = completedStats(data)
        if (totalReps >= 50) unlock(context, "worker")
        if (totalReps >= 500) unlock(context, "athlete")
        if (totalReps >= 1000) unlock(context, "heavy")
        if (totalReps >= 10_000) unlock(context, "titan")

        val repsByExercise = repsByExercise(data)
        val nameReps = { keyword: String ->
            data.exercises
                .filter { it.name.contains(keyword, ignoreCase = true) }
                .sumOf { repsByExercise[it.id] ?: 0 }
        }
        if (nameReps("отжим") >= 1000) unlock(context, "pushup_king")
        if (nameReps("присед") >= 1000) unlock(context, "squat_master")

        val categoryReps = categoryStats(data).mapValues { it.value.reps }
        if ((categoryReps["Пресс"] ?: 0) >= 500) unlock(context, "iron_abs")

        if (dailyStats(data).values.any { it.reps >= 500 }) unlock(context, "one_day_500")

        val allSets = setsByExercise(data).values.flatten()
        if (allSets.any { it >= 100 }) unlock(context, "sprinter")

        val endurance = setsByExercise(data).entries.any { (exerciseId, sets) ->
            val exercise = data.exercises.firstOrNull { it.id == exerciseId }
            exercise?.unit == "Минуты" && sets.any { it > 5 }
        }
        if (endurance) unlock(context, "endurance")
    }

    suspend fun checkSetCount(context: AchievementContext, data: AchievementSnapshot) {
        val (totalSets, _) = completedStats(data)
        if (totalSets >= 1) unlock(context, "first_set")
        if (totalSets >= 10) unlock(context, "novice")

        val handSets = data.exercises
            .filter { it.category == "Руки" }
            .sumOf { exerciseIdSets(data, it.id) }
        if (handSets >= 300) unlock(context, "steel_grip")

        val today = LocalDate.now()
        val daily = dailyCompletion(data, today)
        if (daily.first > 0 && daily.first == daily.second) {
            unlock(context, "first_day")
            unlock(context, "no_skip")
        }

        if (workoutCompletion(data).any { it.second }) {
            unlock(context, "first_finish")
            unlock(context, "perfectionist")
        }
    }

    suspend fun checkCategories(context: AchievementContext, data: AchievementSnapshot) {
        val categories = data.exercises.map { it.category }.toSet()
        if (categories.containsAll(AllCategories)) unlock(context, "collector")
        if (dailyStats(data).values.any { it.categories.size >= 3 }) {
            unlock(context, "triathlon")
        }
    }

    suspend fun checkTime(context: AchievementContext, data: AchievementSnapshot) {
        if (context.hour in 0..7) unlock(context, "early_bird")
        if (context.hour in 22..23) unlock(context, "night_owl")
        if (context.hour in 0..4) unlock(context, "night_warrior")

        data.workouts.forEach { plan ->
            val plannedSets = plan.exercises.sumOf { setsFor(it, plan.workout).size }
            if (plannedSets > 0) {
                val estimatedMinutes = plan.exercises.sumOf { planned ->
                    val sets = setsFor(planned, plan.workout)
                    val rest = DistributionHelper.restTimeMinutes(
                        plan.workout,
                        planned.dailyTargetReps,
                        planned.repsPerSet,
                    )
                    val perSet = DistributionHelper.estimatedTimePerSet(
                        planned.repsPerSet,
                        planned.exercise.unit,
                    )
                    sets.size * (rest + perSet)
                }
                if (estimatedMinutes == plan.workout.availableTimeMinutes) {
                    unlock(context, "perfect_timing")
                }
            }
            val longRest = plan.exercises.any {
                DistributionHelper.restTimeMinutes(
                    plan.workout,
                    it.dailyTargetReps,
                    it.repsPerSet,
                ) > 5
            }
            if (longRest) unlock(context, "patient")
        }
    }

    suspend fun checkWorkoutSettings(context: AchievementContext, data: AchievementSnapshot) {
        if (data.workouts.isNotEmpty()) {
            unlock(context, "first_workout")
            unlock(context, "auto_rest")
        }
        if (data.workouts.any { it.exercises.size >= 2 }) unlock(context, "organizer")
        if (data.workouts.any { it.workout.periodDays >= 30 }) unlock(context, "planner")
        if (data.workouts.any { it.workout.periodDays >= 90 }) unlock(context, "long_term")
        if (data.workouts.any {
                it.workout.sleepStart.isNotBlank() && it.workout.sleepEnd.isNotBlank()
            }
        ) {
            unlock(context, "sleep_setup")
        }
        if (data.workouts.any { totalPlannedSets(it) >= 10 }) unlock(context, "circles")
        if (data.workouts.size >= 100) unlock(context, "legend")

        if (data.exercises.size >= 3) unlock(context, "first_gym")
        if (data.exercises.size >= 10) unlock(context, "expert")

        if ("reps" in context.changedFields) unlock(context, "dynamic")
        if ("availableTime" in context.changedFields) unlock(context, "adapt")
        if ("name" in context.changedFields) unlock(context, "editor")
    }

    /** Прямая разблокировка для событий без полного снимка (синхронизация, уведомление, календарь). */
    suspend fun unlockDirect(dao: AchievementDao, id: String) {
        if (dao.isUnlocked(id)) return
        val achievement = AchievementRepository.byId(id) ?: return
        dao.unlockAchievement(id)
        AchievementEvents.publish("Трофей получен: ${achievement.title} ${achievement.emoji}")
    }

    private suspend fun checkAll(context: AchievementContext, data: AchievementSnapshot) {
        checkStreak(context, data)
        checkTotalReps(context, data)
        checkSetCount(context, data)
        checkCategories(context, data)
        checkTime(context, data)
        checkWorkoutSettings(context, data)

        val unlockedCount = context.achievementDao.getAllAchievements().first().count { it.isUnlocked }
        if (unlockedCount >= 25) unlock(context, "collector_2")
    }

    private suspend fun unlock(context: AchievementContext, id: String) {
        unlockDirect(context.achievementDao, id)
    }

    private suspend fun snapshot(context: AchievementContext): AchievementSnapshot {
        val workoutDao = context.workoutDao
        val workouts = workoutDao.getAllWorkouts().first()
            .withPlans(workoutDao.observePlannedExercises().first())
        return AchievementSnapshot(
            workouts = workouts,
            exercises = context.exerciseDao.getAllExercises().first(),
            progress = context.progressDao.observeAllProgress().first(),
        )
    }

    private fun setsFor(planned: com.nmph.setly.data.local.PlannedExercise, workout: com.nmph.setly.data.local.WorkoutEntity) =
        DistributionHelper.setsForPlan(planned, workout)

    private fun totalPlannedSets(plan: WorkoutPlan): Int =
        plan.exercises.sumOf { setsFor(it, plan.workout).size }

    private fun setsByExercise(data: AchievementSnapshot): Map<Int, List<Int>> =
        data.workouts.flatMap { plan ->
            plan.exercises.map { it.exercise.id to setsFor(it, plan.workout) }
        }.toMap()

    private fun exerciseIdSets(data: AchievementSnapshot, exerciseId: Int): Int =
        data.progress
            .filter { it.exerciseId == exerciseId }
            .sumOf { record ->
                val sets = setsByExercise(data)[exerciseId].orEmpty()
                record.completedSets.count { index -> index in sets.indices }
            }

    private fun completedStats(data: AchievementSnapshot): Pair<Int, Int> {
        var sets = 0
        var reps = 0
        val setsByExercise = setsByExercise(data)
        data.progress.forEach { record ->
            val setsForExercise = setsByExercise[record.exerciseId].orEmpty()
            record.completedSets.distinct().forEach { index ->
                if (index in setsForExercise.indices) {
                    sets++
                    reps += setsForExercise[index]
                }
            }
        }
        return sets to reps
    }

    private fun repsByExercise(data: AchievementSnapshot): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        val setsByExercise = setsByExercise(data)
        data.progress.forEach { record ->
            val sets = setsByExercise[record.exerciseId].orEmpty()
            val reps = record.completedSets.distinct().sumOf { index ->
                sets.getOrNull(index) ?: 0
            }
            result[record.exerciseId] = (result[record.exerciseId] ?: 0) + reps
        }
        return result
    }

    private fun categoryStats(data: AchievementSnapshot): Map<String, DayStats> {
        val result = mutableMapOf<String, DayStats>()
        val repsByExercise = repsByExercise(data)
        data.exercises.forEach { exercise ->
            val stat = result.getOrPut(exercise.category) { DayStats() }
            stat.reps += repsByExercise[exercise.id] ?: 0
            stat.sets += exerciseIdSets(data, exercise.id)
        }
        return result
    }

    private fun dailyStats(data: AchievementSnapshot): Map<LocalDate, DayStats> {
        val result = mutableMapOf<LocalDate, DayStats>()
        val setsByExercise = setsByExercise(data)
        val exerciseById = data.exercises.associateBy { it.id }
        data.progress.forEach { record ->
            val date = runCatching { LocalDate.parse(record.date) }.getOrNull() ?: return@forEach
            val stat = result.getOrPut(date) { DayStats() }
            val sets = setsByExercise[record.exerciseId].orEmpty()
            record.completedSets.distinct().forEach { index ->
                if (index in sets.indices) {
                    stat.reps += sets[index]
                    stat.sets++
                }
            }
            exerciseById[record.exerciseId]?.category?.let { stat.categories += it }
        }
        return result
    }

    private fun trainingDays(data: AchievementSnapshot): List<LocalDate> =
        dailyStats(data).filter { it.value.sets > 0 }.keys.sorted()

    private fun longestStreak(data: AchievementSnapshot): Int {
        var best = 0
        var current = 0
        var previous: LocalDate? = null
        trainingDays(data).forEach { date ->
            current = if (previous?.plusDays(1) == date) current + 1 else 1
            best = maxOf(best, current)
            previous = date
        }
        return best
    }

    private fun hasReturn(data: AchievementSnapshot): Boolean {
        var previous: LocalDate? = null
        trainingDays(data).forEach { date ->
            val gap = previous?.let { date.toEpochDay() - it.toEpochDay() } ?: 0
            if (gap > 1) return true
            previous = date
        }
        return false
    }

    private fun isActiveOn(workout: WorkoutPlan, date: LocalDate): Boolean {
        val start = java.time.Instant.ofEpochMilli(workout.workout.startDate)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        return !date.isBefore(start) &&
            !date.isAfter(start.plusDays(workout.workout.periodDays.toLong() - 1))
    }

    private fun dailyCompletion(data: AchievementSnapshot, date: LocalDate): Pair<Int, Int> {
        val dateString = date.toString()
        var planned = 0
        var done = 0
        data.workouts.filter { isActiveOn(it, date) }.forEach { plan ->
            plan.exercises.forEach { exercise ->
                val sets = setsFor(exercise, plan.workout)
                planned += sets.size
                val record = data.progress.firstOrNull {
                    it.date == dateString && it.exerciseId == exercise.exercise.id
                }
                done += record?.completedSets?.distinct()?.count { index -> index in sets.indices } ?: 0
            }
        }
        return planned to done
    }

    private fun workoutCompletion(data: AchievementSnapshot): List<Pair<WorkoutPlan, Boolean>> =
        data.workouts.map { plan ->
            val start = java.time.Instant.ofEpochMilli(plan.workout.startDate)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            val end = start.plusDays(plan.workout.periodDays.toLong() - 1)
            var planned = 0
            var done = 0
            plan.exercises.forEach { exercise ->
                val sets = setsFor(exercise, plan.workout)
                planned += sets.size
                data.progress
                    .filter { record ->
                        val recordDate = runCatching { LocalDate.parse(record.date) }.getOrNull()
                        record.exerciseId == exercise.exercise.id &&
                            recordDate != null && !recordDate.isBefore(start) && !recordDate.isAfter(end)
                    }
                    .forEach { record ->
                        done += record.completedSets.distinct().count { index -> index in sets.indices }
                    }
            }
            plan to (planned > 0 && done >= planned)
        }
}
