package com.nmph.setly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Тренировка: период и окно сна. Дневная норма задаётся для каждого упражнения отдельно. */
@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val startDate: Long,
    val periodDays: Int,
    val sleepStart: String, // "HH:mm"
    val sleepEnd: String, // "HH:mm"
)
