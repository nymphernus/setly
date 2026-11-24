package com.nmph.setly.utils

import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.PlannedExercise
import com.nmph.setly.data.local.ProgressEntity
import com.nmph.setly.data.local.WorkoutEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/**
 * Единый источник истины для расчёта подходов, отдыха и долгов.
 *
 * 1. Подходów за день: ceil(dailyTargetReps / repsPerSet).
 * 2. Отдых считается только от времени бодрствования: (24ч - сон) / число подходов, 1..120 минут.
 * 3. Недоделанная норма за прошлый день становится долгом и распределяется на 7 дней вперёд.
 */
object DistributionHelper {
    const val DEFAULT_DAILY_TARGET_REPS = 300
    const val DEFAULT_REPS_PER_SET = 20
    const val MAX_REST_MINUTES = 120
    const val DEBT_WINDOW_DAYS = 7
    private const val MINUTES_PER_DAY = 24 * 60
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

    /** Время бодрствования в минутах: 24 часа без сна. */
    fun awakeMinutes(sleepStart: String, sleepEnd: String): Int =
        (MINUTES_PER_DAY - sleepMinutes(sleepStart, sleepEnd)).coerceAtLeast(1)

    /** Число подходов за день: 300 / 30 = 10, 100 / 30 = 4. */
    fun totalSetsPerDay(dailyTargetReps: Int, repsPerSet: Int): Int {
        if (dailyTargetReps <= 0) return 0
        val safeReps = if (repsPerSet > 0) repsPerSet else DEFAULT_REPS_PER_SET
        return ceil(dailyTargetReps.toDouble() / safeReps).toInt().coerceAtLeast(1)
    }

    /** Разбиение дневной нормы на подходы: все подходы равные, последний — остаток. */
    fun setsForDay(dailyTargetReps: Int, repsPerSet: Int): List<Int> {
        val sets = totalSetsPerDay(dailyTargetReps, repsPerSet)
        if (sets == 0) return emptyList()
        val safeReps = if (repsPerSet > 0) repsPerSet else DEFAULT_REPS_PER_SET
        return (0 until sets).map { index ->
            if (index == sets - 1) {
                dailyTargetReps - safeReps * (sets - 1)
            } else {
                safeReps
            }
        }.map { it.coerceAtLeast(1) }
    }

    /** Отдых между подходами: всё время бодрствования делится на число подходов. */
    fun restMinutes(sleepStart: String, sleepEnd: String, totalSetsPerDay: Int): Int {
        if (totalSetsPerDay <= 0) return MAX_REST_MINUTES
        return (awakeMinutes(sleepStart, sleepEnd) / totalSetsPerDay)
            .coerceIn(1, MAX_REST_MINUTES)
    }

    /** Отдых для конкретного упражнения тренировки. */
    fun restTimeMinutes(
        workout: WorkoutEntity,
        dailyTargetReps: Int,
        repsPerSet: Int,
    ): Int = restMinutes(
        workout.sleepStart,
        workout.sleepEnd,
        totalSetsPerDay(dailyTargetReps, repsPerSet),
    )

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

    /**
     * Базовые подходы упражнения. Время на тренировку больше не ограничивает число подходов:
     * оно определяется только дневной нормой и размером подхода.
     */
    @Suppress("UNUSED_PARAMETER")
    fun setsForPlan(plan: PlannedExercise, workout: WorkoutEntity): List<Int> =
        setsForDay(plan.dailyTargetReps, plan.repsPerSet)

    /** Подходы скорректированной (с учётом долгов) нормы. */
    fun setsForAdjustedTarget(plan: PlannedExercise, adjustedTargetReps: Int): List<Int> =
        setsForDay(adjustedTargetReps, plan.repsPerSet)

    /** Размер подхода, который реально получился после распределения. */
    fun plannedRepsPerSet(plan: PlannedExercise, workout: WorkoutEntity): Int =
        setsForPlan(plan, workout).firstOrNull() ?: 0

    /**
     * Доля долга, которая переносится на день с номером [dayOffset] (1..7) после пропущенного дня.
     * Долг 10 → 2, 2, 2, 1, 1, 1, 1 (сумма ровно 10).
     */
    fun debtShare(debt: Int, dayOffset: Int): Int {
        if (debt <= 0 || dayOffset !in 1..DEBT_WINDOW_DAYS) return 0
        val base = debt / DEBT_WINDOW_DAYS
        val remainder = debt % DEBT_WINDOW_DAYS
        return base + if (dayOffset <= remainder) 1 else 0
    }

    /** Выполненные повторения по упражнению за конкретный день. */
    fun completedRepsForDate(
        exerciseId: Int,
        date: LocalDate,
        progressList: List<ProgressEntity>,
        baseSets: List<Int>,
    ): Int = progressList
        .firstOrNull { it.exerciseId == exerciseId && it.date == date.toString() }
        ?.completedSets
        .orEmpty()
        .distinct()
        .sumOf { index -> baseSets.getOrNull(index) ?: 0 }

    /**
     * Норма на сегодня с учётом долгов за предыдущие 7 дней.
     * Каждый предыдущий день добавляет свою долю в зависимости от того, сколько дней прошло.
     */
    fun calculateAdjustedTarget(
        exercise: PlannedExercise,
        date: LocalDate,
        progressList: List<ProgressEntity>,
        workout: WorkoutEntity? = null,
    ): Int {
        val base = exercise.dailyTargetReps.coerceAtLeast(0)
        if (base == 0) return 0
        val baseSets = setsForDay(base, exercise.repsPerSet)
        var adjusted = base
        for (offset in 1..DEBT_WINDOW_DAYS) {
            val past = date.minusDays(offset.toLong())
            if (workout != null && !isActiveOn(workout, past)) continue
            val done = completedRepsForDate(exercise.exercise.id, past, progressList, baseSets)
            val debt = (base - done).coerceAtLeast(0)
            adjusted += debtShare(debt, offset)
        }
        return adjusted
    }

    /** Норма на конкретный день: используется календарём и детализацией. */
    fun adjustedTargetForDate(
        exercise: PlannedExercise,
        workout: WorkoutEntity?,
        date: LocalDate,
        progressList: List<ProgressEntity>,
    ): Int = calculateAdjustedTarget(exercise, date, progressList, workout)

    private fun isActiveOn(workout: WorkoutEntity, date: LocalDate): Boolean {
        val start = Instant.ofEpochMilli(workout.startDate)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return !date.isBefore(start) &&
            !date.isAfter(start.plusDays(workout.periodDays.toLong() - 1))
    }

    /** Единицы измерения по умолчанию для упражнений без явного значения. */
    internal fun defaultUnit(exercise: ExerciseEntity): String =
        exercise.unit.ifBlank { ExerciseEntity.DEFAULT_UNIT }
}
