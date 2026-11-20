package com.nmph.setly.utils

import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.WorkoutEntity
import kotlin.math.ceil

/**
 * Распределение повторений по дням и расчёт отдыха.
 *
 * Правила (единый источник истины для всех экранов):
 * 1. Дневная норма = ceil(targetReps / periodDays).
 * 2. Подходы дня = дневная норма, разбитая на куски по repsPerSet.
 * 3. Отдых = (24 * 60) / число подходов дня, но не больше MAX_REST_MINUTES.
 */
object DistributionHelper {
    const val DEFAULT_REPS_PER_SET = 20
    const val DEFAULT_TARGET_REPS = 300
    const val DEFAULT_REST_MINUTES = 30
    const val MAX_REST_MINUTES = 120

    /** Целочисленный аналог ceil(a / b) без ошибок округления Double. */
    private fun ceilDiv(value: Int, divisor: Int): Int =
        if (divisor <= 0) 0 else (value + divisor - 1) / divisor

    /** Сколько повторений нужно сделать в один день тренировки. */
    fun dailyReps(workout: WorkoutEntity, exercise: ExerciseEntity): Int {
        if (exercise.targetReps <= 0) return 0
        return ceilDiv(exercise.targetReps, workout.periodDays.coerceAtLeast(1))
    }

    /** Совместимость с экранами, которые работают с картой целей по упражнениям. */
    fun calculateTargetsForWorkout(
        workout: WorkoutEntity,
        exercises: List<ExerciseEntity>,
    ): Map<Int, Int> = exercises.associate { it.id to dailyReps(workout, it) }

    /**
     * Подходы для дневной нормы: 43 повторения при repsPerSet = 20 → [20, 20, 3].
     * Значение 10 при repsPerSet = 20 → [10] (один укороченный подход).
     */
    fun getSetsForExercise(
        exercise: ExerciseEntity,
        targetRepsForToday: Int,
        repsPerSet: Int = exercise.repsPerSet,
    ): List<Int> {
        val safeRepsPerSet = when {
            repsPerSet > 0 -> repsPerSet
            exercise.repsPerSet > 0 -> exercise.repsPerSet
            else -> DEFAULT_REPS_PER_SET
        }
        return splitIntoSets(targetRepsForToday, safeRepsPerSet)
    }

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

    /**
     * Отдых между подходами в минутах.
     *
     * totalSetsPerDay — количество подходов на сегодня, а не общее число повторений.
     * 15 подходов → 96 минут; 1 подход → 120 минут (ограничение сверху).
     */
    fun effectiveRestMinutes(
        workout: WorkoutEntity,
        totalSetsPerDay: Int,
        autoRestEnabled: Boolean = true,
    ): Int {
        val minutes = if (workout.autoRestTime && autoRestEnabled) {
            if (totalSetsPerDay <= 0) DEFAULT_REST_MINUTES else (24 * 60) / totalSetsPerDay
        } else {
            workout.restTimeMinutes
        }
        return minutes.coerceIn(1, MAX_REST_MINUTES)
    }
}
