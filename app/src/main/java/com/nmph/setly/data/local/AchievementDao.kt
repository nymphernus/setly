package com.nmph.setly.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY id ASC")
    fun getAllAchievements(): Flow<List<AchievementEntity>>

    @Query(
        "UPDATE achievements SET isUnlocked = 1, unlockedDate = :unlockedDate WHERE id = :id",
    )
    suspend fun unlockAchievement(id: String, unlockedDate: Long = System.currentTimeMillis())

    @Query("SELECT isUnlocked FROM achievements WHERE id = :id LIMIT 1")
    suspend fun isUnlocked(id: String): Boolean
}
