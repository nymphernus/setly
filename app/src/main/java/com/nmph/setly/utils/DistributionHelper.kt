package com.nmph.setly.utils

import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.ProgressEntity
import com.nmph.setly.data.local.WorkoutEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.floor

object DistributionHelper {
    // Пока размер подхода фиксированный; позже можно вынести в настройки упражнения.
    const val DEFAULT_REPS_PER_SET = 20

    // Переход через полночь (21:00 → 08:00) даёт 11 часов, а не -13.
    fun calculateSleepPenalty(workout: WorkoutEntity): Double {
        val start = LocalTime.parse(workout.sleepStart)
        val end = LocalTime.parse(workout.sleepEnd)
        var minutes = ChronoUnit.MINUTES.between(start, end)
        if (minutes < 0) minutes += 24 * 60
        val hours = minutes / 60.0
        return if (hours < 7) 0.8 else 1.1
    }

    fun calculateTargetForToday(
        workout: WorkoutEntity,
        exercise: ExerciseEntity,
        today: LocalDate,
        progressList: List<ProgressEntity>,
    ): Int {
        val safePeriod = workout.periodDays.coerceAtLeast(1)
        val base = exercise.targetReps.toDouble() / safePeriod
        // floor, чтобы не перегружать пользователя: 7.85 → 7.
        val dailyTarget = floor(base * calculateSleepPenalty(workout)).toInt()

        val carryOver = progressList
            .filter { it.date < today.toString() && it.exerciseId == exercise.id }
            .map { past ->
                val pastTarget = calculateTargetForToday(
                    workout, exercise, LocalDate.parse(past.date), progressList,
                )
                (pastTarget - past.completedSets.sum()).coerceAtLeast(0)
            }
            .sum()

        return dailyTarget + carryOver
    }

    fun calculateTargetsForWorkout(
        workout: WorkoutEntity,
        exercises: List<ExerciseEntity>,
        today: LocalDate,
        progressList: List<ProgressEntity>,
    ): Map<Int, Int> = exercises.associate { exercise ->
        exercise.id to calculateTargetForToday(workout, exercise, today, progressList)
    }

    // Авто-отдых: 24 часа делим на число подходов за день (300/20 = 15 → 96 минут).
    fun effectiveRestMinutes(workout: WorkoutEntity, totalSetsPerDay: Int): Int {
        if (!workout.autoRestTime || totalSetsPerDay <= 0) return workout.restTimeMinutes
        return (24 * 60) / totalSetsPerDay
    }

    // 300 / 20 → 15 подходов по 20; 50 / 20 → [20, 20, 10].
    fun splitIntoSets(targetReps: Int, repsPerSet: Int): List<Int> {
        require(repsPerSet > 0) { "repsPerSet must be positive" }
        if (targetReps <= 0) return emptyList()

        val result = mutableListOf<Int>()
        var remaining = targetReps
        while (remaining > 0) {
            val current = remaining.coerceAtMost(repsPerSet)
            result += current
            remaining -= current
        }
        return result
    }
}
