package com.nmph.setly.data.local

import androidx.room.Embedded

/** Упражнение в составе тренировки с его дневной нормой. */
data class PlannedExercise(
    val exercise: ExerciseEntity,
    val dailyTargetReps: Int,
)

/** Тренировка вместе с дневными нормами всех её упражнений. */
data class WorkoutPlan(
    val workout: WorkoutEntity,
    val exercises: List<PlannedExercise>,
)

/** Плоская строка JOIN-запроса: упражнение + дневная норма + ID тренировки. */
data class PlannedExerciseRow(
    val workoutId: Int,
    @Embedded val exercise: ExerciseEntity,
    val dailyTargetReps: Int,
)

/** Собирает планы тренировок из двух потоков DAO. */
fun List<WorkoutEntity>.withPlans(rows: List<PlannedExerciseRow>): List<WorkoutPlan> =
    map { workout ->
        WorkoutPlan(
            workout = workout,
            exercises = rows.filter { it.workoutId == workout.id }
                .map { PlannedExercise(it.exercise, it.dailyTargetReps) },
        )
    }
