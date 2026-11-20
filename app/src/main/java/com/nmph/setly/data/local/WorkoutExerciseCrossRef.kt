package com.nmph.setly.data.local

import androidx.room.Entity

@Entity(tableName = "workout_exercise_cross_ref", primaryKeys = ["workoutId", "exerciseId"])
data class WorkoutExerciseCrossRef(
    val workoutId: Int,
    val exerciseId: Int,
)
