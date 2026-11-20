package com.nmph.setly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    // Общее число повторений за всю тренировку; период живёт в WorkoutEntity.
    val targetReps: Int,
)
