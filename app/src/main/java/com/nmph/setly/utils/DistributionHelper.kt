package com.nmph.setly.utils

import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.PlannedExercise
import com.nmph.setly.data.local.WorkoutEntity
import kotlin.math.ceil

/**
 * Единый источник истины для расчёта дневных подходов и отдыха.
 *
 * 1. Дневная норма хранится в связи тренировка↔упражнение (dailyTargetReps).
 * 2. Число подходов ограничено доступным временем тренировки:
 *    maxSets = availableTimeMinutes / (restTimeMinutes + estimatedTimePerSet), минимум 1.
 * 3. Дневная норма делится на maxSets подходов с округлением размера вверх.
 * 4. Отдых для таймера считаем только из бодрствования: (24ч - сон) / число подходов.
 */
object DistributionHelper {
    const val DEFAULT_DAILY_TARGET_REPS = 300
    const val DEFAULT_REPS_PER_SET = 20
    const val MAX_REST_MINUTES = 120
    private const val MINUTES_PER_DAY = 24 * 60

    // Практический верхний предел отдыха между подходами при планировании по времени.
    private const val PLANNING_REST_MAX_MINUTES = 10
    private const val SECONDS_PER_REP = 3

    private fun parseMinutes(value: String): Int {
        val parts = value.split(":")
        if (parts.size != 2) return 0
        val hours = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: 0
        val minutes = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: 0
        return hours * 60 + minutes
    }

    /** Длительность сна в минутах. Для 22:00–08:00 это 600 минут (10 часов). */
    fun sleepMinutes(sleepStart: String, sleepEnd: String): Int {
        val start = parseMinutes(sleepStart)
        val end = parseMinutes(sleepEnd)
        val diff = end - start
        return (if (diff <= 0) diff + MINUTES_PER_DAY else diff)
            .coerceIn(0, MINUTES_PER_DAY)
    }

    /** Разбиение дневной нормы на подходы фиксированного размера. */
    fun setsForDay(dailyTargetReps: Int, repsPerSet: Int): List<Int> {
        val safeRepsPerSet = if (repsPerSet > 0) repsPerSet else DEFAULT_REPS_PER_SET
        if (dailyTargetReps <= 0) return emptyList()

        val result = mutableListOf<Int>()
        var remaining = dailyTargetReps
        while (remaining > 0) {
            val current = remaining.coerceAtMost(safeRepsPerSet)
            result += current
            remaining -= current
        }
        return result
    }

    /**
     * Отдых между подходами в минутах, используемый в планировании.
     * Учитывает окно сна тренировки, но ограничен разумным пределом для одного подхода.
     */
    fun restTimeMinutes(
        workout: WorkoutEntity,
        dailyTargetReps: Int,
        repsPerSet: Int,
    ): Int {
        val safeReps = if (repsPerSet > 0) repsPerSet else DEFAULT_REPS_PER_SET
        val naiveSets = maxOf(1, ceil(dailyTargetReps.toDouble() / safeReps).toInt())
        return restMinutes(workout.sleepStart, workout.sleepEnd, naiveSets)
            .coerceIn(1, PLANNING_REST_MAX_MINUTES)
    }

    /** Оценка длительности одного подхода в минутах (минимум минута). */
    fun estimatedTimePerSet(repsPerSet: Int, unit: String): Int {
        val safeReps = if (repsPerSet > 0) repsPerSet else DEFAULT_REPS_PER_SET
        val minutes = when (unit) {
            "Минуты" -> safeReps
            "Секунды" -> ceil(safeReps / 60.0).toInt()
            else -> ceil(safeReps * SECONDS_PER_REP / 60.0).toInt()
        }
        return minutes.coerceAtLeast(1)
    }

    /** maxSets = availableTimeMinutes / (restTimeMinutes + estimatedTimePerSet), минимум 1. */
    fun maxSetsForTime(
        availableTimeMinutes: Int,
        restTimeMinutes: Int,
        estimatedTimePerSet: Int,
    ): Int {
        val perSet = restTimeMinutes.coerceAtLeast(0) + estimatedTimePerSet.coerceAtLeast(0)
        if (perSet <= 0 || availableTimeMinutes <= 0) return 1
        return (availableTimeMinutes / perSet).coerceAtLeast(1)
    }

    /** Делит дневную норму на maxSets подходов, округляя размер подхода вверх. */
    fun splitEvenly(dailyTargetReps: Int, maxSets: Int): List<Int> {
        if (dailyTargetReps <= 0) return emptyList()
        val sets = maxSets.coerceAtLeast(1)
        val perSet = ceil(dailyTargetReps.toDouble() / sets).toInt().coerceAtLeast(1)
        val result = mutableListOf<Int>()
        var remaining = dailyTargetReps
        while (remaining > 0 && result.size < sets) {
            val current = remaining.coerceAtMost(perSet)
            result += current
            remaining -= current
        }
        return result
    }

    /** Подходы упражнения с учётом доступного времени тренировки. */
    fun setsForPlan(plan: PlannedExercise, workout: WorkoutEntity): List<Int> {
        val rest = restTimeMinutes(workout, plan.dailyTargetReps, plan.repsPerSet)
        val estimated = estimatedTimePerSet(plan.repsPerSet, defaultUnit(plan.exercise))
        val maxSets = maxSetsForTime(workout.availableTimeMinutes, rest, estimated)
        return splitEvenly(plan.dailyTargetReps, maxSets)
    }

    /** Размер подхода, который реально получился после time-based распределения. */
    fun plannedRepsPerSet(plan: PlannedExercise, workout: WorkoutEntity): Int =
        setsForPlan(plan, workout).firstOrNull() ?: 0

    /**
     * Отдых между подходами в минутах.
     * 22:00–08:00 и 10 подходов → (1440 - 600) / 10 = 84 минуты.
     */
    fun restMinutes(sleepStart: String, sleepEnd: String, totalSetsPerDay: Int): Int {
        val awakeMinutes = (MINUTES_PER_DAY - sleepMinutes(sleepStart, sleepEnd)).coerceAtLeast(1)
        if (totalSetsPerDay <= 0) return MAX_REST_MINUTES
        return (awakeMinutes / totalSetsPerDay).coerceIn(1, MAX_REST_MINUTES)
    }

    /** Единицы измерения по умолчанию для упражнений без явного значения. */
    internal fun defaultUnit(exercise: ExerciseEntity): String =
        exercise.unit.ifBlank { ExerciseEntity.DEFAULT_UNIT }
}
