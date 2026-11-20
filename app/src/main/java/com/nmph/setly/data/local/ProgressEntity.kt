package com.nmph.setly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey
    val id: String, // "YYYY-MM-DD_<exerciseId>"
    val date: String,
    val exerciseId: Int,
    // Выполненные подходы: [20, 20, 10]. В БД хранится строкой через Converters.
    val completedSets: List<Int> = emptyList(),
    val isCompleted: Boolean = false,
)
