package com.nmph.setly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Трофей: описание берётся из справочника, состояние хранится в БД. */
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val isUnlocked: Boolean = false,
    val unlockedDate: Long? = null,
)
