package com.nmph.setly.utils

import com.nmph.setly.data.local.PlannedExercise

/**
 * Единый источник истины для расчёта дневных подходов и отдыха.
 *
 * 1. Дневная норма уже хранится в тренировке (dailyTargetReps) — делить на период больше не нужно.
 * 2. Подходы дня получаем разбиением дневной нормы на repsPerSet.
 * 3. Отдых считаем только из бодрствования: (24ч - сон) / число подходов, максимум 120 мин.
 */
object DistributionHelper {
    const val DEFAULT_DAILY_TARGET_REPS = 300
    const val DEFAULT_REPS_PER_SET = 20
    const val MAX_REST_MINUTES = 120
    private const val MINUTES_PER_DAY = 24 * 60

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

    /** 300 повторений по 30 в подходе → десять подходов по 30. */
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

    fun setsForPlan(plan: PlannedExercise): List<Int> =
        setsForDay(plan.dailyTargetReps, plan.repsPerSet)

    /**
     * Отдых между подходами в минутах.
     * 22:00–08:00 и 10 подходов → (1440 - 600) / 10 = 84 минуты.
     */
    fun restMinutes(sleepStart: String, sleepEnd: String, totalSetsPerDay: Int): Int {
        val awakeMinutes = (MINUTES_PER_DAY - sleepMinutes(sleepStart, sleepEnd)).coerceAtLeast(1)
        if (totalSetsPerDay <= 0) return MAX_REST_MINUTES
        return (awakeMinutes / totalSetsPerDay).coerceIn(1, MAX_REST_MINUTES)
    }
}
