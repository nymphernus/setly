package com.nmph.setly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Тренировка: период, окно сна и настройки напоминаний. Временные окна считаются от бодрствования. */
@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val startDate: Long,
    /** Момент создания тренировки: якорь для динамического расписания в день создания. */
    val createdAt: Long = 0L,
    val periodDays: Int,
    val sleepStart: String, // "HH:mm"
    val sleepEnd: String, // "HH:mm"
    val notificationsEnabled: Boolean = true,
    val remindBeforeMinutes: Int = DEFAULT_REMIND_BEFORE_MINUTES,
) {
    companion object {
        const val DEFAULT_REMIND_BEFORE_MINUTES = 5
    }
}
