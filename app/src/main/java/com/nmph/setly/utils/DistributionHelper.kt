package com.nmph.setly.utils

import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.PlannedExercise
import com.nmph.setly.data.local.ProgressEntity
import com.nmph.setly.data.local.WorkoutEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/** Окно одного подхода в минутах от полуночи. Конец меньше начала — окно пересекает полночь. */
data class TimeWindow(
    val startMinute: Int,
    val endMinute: Int,
) {
    val wrapsMidnight: Boolean get() = endMinute < startMinute

    val durationMinutes: Int get() = if (wrapsMidnight) {
        DistributionHelper.MINUTES_PER_DAY - startMinute + endMinute
    } else {
        endMinute - startMinute
    }

    val startLabel: String get() = DistributionHelper.formatMinute(startMinute)
    val endLabel: String get() = DistributionHelper.formatMinute(endMinute)
    val label: String get() = "$startLabel–$endLabel"
}

/**
 * Единый источник истины для расчёта подходов, временных окон и долгов.
 *
 * 1. Подходы за день: ceil(dailyTargetReps / repsPerSet).
 * 2. Время бодрствования делится на окна подходов, по одному окну на каждый подход упражнения.
 * 3. Недоделанная норма за прошлый день становится долгом и распределяется на 7 дней вперёд.
 */
object DistributionHelper {
    const val DEFAULT_DAILY_TARGET_REPS = 300
    const val DEFAULT_REPS_PER_SET = 20
    const val DEBT_WINDOW_DAYS = 7
    const val MINUTES_PER_DAY = 24 * 60

    /** Время «HH:mm» в минутах от полуночи (0..1439). */
    fun minutesOfDay(value: String): Int {
        val parts = value.split(":")
        if (parts.size != 2) return 0
        val hours = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: 0
        val minutes = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: 0
        return hours * 60 + minutes
    }

    /** Формат «HH:mm» для минут от полуночи (1440 отображается как 24:00). */
    fun formatMinute(minute: Int): String {
        if (minute == MINUTES_PER_DAY) return "24:00"
        val normalized = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return "%02d:%02d".format(normalized / 60, normalized % 60)
    }

    /** Длительность сна в минутах. Для 22:00–08:00 это 600 минут (10 часов). */
    fun sleepMinutes(sleepStart: String, sleepEnd: String): Int {
        val start = minutesOfDay(sleepStart)
        val end = minutesOfDay(sleepEnd)
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

    /**
     * Временные окна для одного упражнения: всё время бодрствования делится на [totalSets]
     * равных окон, начиная с момента пробуждения [sleepEnd]. Остаток делится поровну
     * между первыми окнами, чтобы покрыть бодрствование без потери минут.
     *
     * Для сна 22:00–08:00 и 10 подходов окна идут с 08:00 до 22:00 по 84 минуты.
     * Если бодрствование переходит через полночь, у соответствующего окна
     * [TimeWindow.endMinute] меньше [TimeWindow.startMinute].
     */
    fun calculateWindowsForExercise(
        sleepStart: String,
        sleepEnd: String,
        totalSets: Int,
    ): List<TimeWindow> {
        if (totalSets <= 0) return emptyList()
        val awake = awakeMinutes(sleepStart, sleepEnd)
        val baseLength = awake / totalSets
        val remainder = awake % totalSets
        val wakeMinute = minutesOfDay(sleepEnd)

        var offset = 0
        return (0 until totalSets).map { index ->
            val length = baseLength + if (index < remainder) 1 else 0
            val start = (wakeMinute + offset) % MINUTES_PER_DAY
            val rawEnd = wakeMinute + offset + length
            offset += length
            val end = if (rawEnd == MINUTES_PER_DAY) {
                MINUTES_PER_DAY
            } else {
                rawEnd % MINUTES_PER_DAY
            }
            TimeWindow(start, end)
        }
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
