package com.nmph.setly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey
    val id: String, // "YYYY-MM-DD_<exerciseId>"
    val date: String,
    val exerciseId: Int,
    // Индексы выполненных подходов (0-based): [0, 2] — отмечены 1-й и 3-й подходы.
    // В БД хранится строкой через Converters.
    val completedSets: List<Int> = emptyList(),
    val isCompleted: Boolean = false,
)
