package com.nmph.setly.data.local

import androidx.room.Entity

/**
 * Связь тренировки и упражнения.
 *
 * dailyTargetReps — сколько повторений нужно сделать за один день именно в этой
 * тренировке. repsPerSet — сколько раз в подходе. Оба поля живут здесь, а не в
 * ExerciseEntity, чтобы одно упражнение могло использоваться в разных тренировках
 * с разными целями и разным размером подхода.
 */
@Entity(tableName = "workout_exercise_cross_ref", primaryKeys = ["workoutId", "exerciseId"])
data class WorkoutExerciseCrossRef(
    val workoutId: Int,
    val exerciseId: Int,
    val dailyTargetReps: Int,
    val repsPerSet: Int,
)

/** Настройки упражнения, приходящие из экрана создания/редактирования тренировки. */
data class WorkoutExerciseSettings(
    val exerciseId: Int,
    val dailyTargetReps: Int,
    val repsPerSet: Int,
)
