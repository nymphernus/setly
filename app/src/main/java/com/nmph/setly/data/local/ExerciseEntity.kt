package com.nmph.setly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Глобальное упражнение: только название и размер одного подхода. */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val repsPerSet: Int = 20,
)
