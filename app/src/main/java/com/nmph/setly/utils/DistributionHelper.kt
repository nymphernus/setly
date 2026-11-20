package com.nmph.setly.utils

import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.ProgressEntity
import java.time.LocalDate
import kotlin.math.roundToInt

object DistributionHelper {
    fun calculateTargetForToday(
        exercise: ExerciseEntity,
        today: LocalDate,
        progressList: List<ProgressEntity>,
    ): Int {
        val safePeriod = exercise.periodDays.coerceAtLeast(1)
        val base = exercise.targetReps.toFloat() / safePeriod
        // Мало сна снижаем нагрузку, 7+ часов дают бонус; попадание в 6 часов нейтрально.
        val sleepFactor = when {
            exercise.sleepHours < 6 -> 0.8f
            exercise.sleepHours >= 7 -> 1.1f
            else -> 1.0f
        }
        val dailyTarget = (base * sleepFactor).roundToInt()

        // Рекурсивно считаем цель каждого прошлого дня, чтобы узнать размер недоделки.
        val carryOver = progressList
            .filter { it.date < today.toString() && it.exerciseId == exercise.id }
            .map { past ->
                val pastDate = LocalDate.parse(past.date)
                val pastTarget = calculateTargetForToday(exercise, pastDate, progressList)
                (pastTarget - past.completedReps).coerceAtLeast(0)
            }
            .sum()

        return dailyTarget + carryOver
    }
}
