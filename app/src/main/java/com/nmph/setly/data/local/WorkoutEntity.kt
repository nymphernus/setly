package com.nmph.setly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Тренировка: период, окно сна, время на тренировку и настройки напоминаний. */
@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val startDate: Long,
    val periodDays: Int,
    val sleepStart: String, // "HH:mm"
    val sleepEnd: String, // "HH:mm"
    val availableTimeMinutes: Int = DEFAULT_AVAILABLE_TIME_MINUTES,
    val notificationsEnabled: Boolean = true,
    val remindBeforeMinutes: Int = DEFAULT_REMIND_BEFORE_MINUTES,
) {
    companion object {
        const val DEFAULT_AVAILABLE_TIME_MINUTES = 60
        const val DEFAULT_REMIND_BEFORE_MINUTES = 5
    }
}
