package com.nmph.setly.data.local

import androidx.room.Embedded

/** Упражнение в составе тренировки с дневной нормой и размером подхода. */
data class PlannedExercise(
    val exercise: ExerciseEntity,
    val dailyTargetReps: Int,
    val repsPerSet: Int,
)

/** Тренировка вместе с настройками всех её упражнений. */
data class WorkoutPlan(
    val workout: WorkoutEntity,
    val exercises: List<PlannedExercise>,
)

/** Плоская строка JOIN-запроса: упражнение + настройки + ID тренировки. */
data class PlannedExerciseRow(
    val workoutId: Int,
    @Embedded val exercise: ExerciseEntity,
    val dailyTargetReps: Int,
    val repsPerSet: Int,
)

/** Собирает планы тренировок из двух потоков DAO. */
fun List<WorkoutEntity>.withPlans(rows: List<PlannedExerciseRow>): List<WorkoutPlan> =
    map { workout ->
        WorkoutPlan(
            workout = workout,
            exercises = rows.filter { it.workoutId == workout.id }
                .map { PlannedExercise(it.exercise, it.dailyTargetReps, it.repsPerSet) },
        )
    }
