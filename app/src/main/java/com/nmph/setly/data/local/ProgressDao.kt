package com.nmph.setly.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE date = :date")
    fun getProgressForDate(date: String): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE date BETWEEN :startDate AND :endDate")
    fun getProgressForPeriod(startDate: String, endDate: String): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE id = :id")
    suspend fun getProgressById(id: String): ProgressEntity?

    @Query("SELECT * FROM progress ORDER BY date DESC")
    suspend fun getAllProgress(): List<ProgressEntity>

    // Upsert вместо insert: повторное нажатие +1/-1 обновляет ту же запись "дата_упражнение".
    @Upsert
    suspend fun insertOrUpdate(progress: ProgressEntity)
}
