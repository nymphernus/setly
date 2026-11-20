package com.nmph.setly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey
    val id: String, // "YYYY-MM-DD_<exerciseId>"
    val date: String,
    val exerciseId: Int,
    val completedReps: Int,
    val isCompleted: Boolean,
)
