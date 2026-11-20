package com.nmph.setly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val startDate: Long,
    val periodDays: Int,
    val sleepStart: String, // "HH:mm"
    val sleepEnd: String, // "HH:mm"
    val restTimeMinutes: Int = 30, // отдых между подходами, если autoRestTime = false
    val autoRestTime: Boolean = false, // true — отдых считаем автоматически из числа подходов
)
