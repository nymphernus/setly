package com.nmph.setly.utils

import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.ProgressEntity
import com.nmph.setly.data.local.WorkoutEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.floor

object DistributionHelper {
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
                (pastTarget - past.completedReps).coerceAtLeast(0)
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
}
