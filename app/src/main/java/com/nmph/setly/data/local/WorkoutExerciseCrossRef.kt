package com.nmph.setly.data.local

import androidx.room.Entity

/**
 * Связь тренировки и упражнения.
 *
 * dailyTargetReps — сколько повторений нужно сделать за один день именно в этой
 * тренировке. Хранится здесь, а не в ExerciseEntity, чтобы одно и то же
 * упражнение могло иметь разные дневные нормы в разных тренировках.
 */
@Entity(tableName = "workout_exercise_cross_ref", primaryKeys = ["workoutId", "exerciseId"])
data class WorkoutExerciseCrossRef(
    val workoutId: Int,
    val exerciseId: Int,
    val dailyTargetReps: Int,
)
