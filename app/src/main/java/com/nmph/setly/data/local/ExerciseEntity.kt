package com.nmph.setly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Глобальное упражнение: описание хранится один раз, размер подхода задаётся в тренировке. */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val description: String = "",
)
