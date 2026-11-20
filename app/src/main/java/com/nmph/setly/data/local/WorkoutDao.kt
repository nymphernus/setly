package com.nmph.setly.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workout: WorkoutEntity): Long

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Delete
    suspend fun delete(workout: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(crossRefs: List<WorkoutExerciseCrossRef>)

    @Query("DELETE FROM workout_exercise_cross_ref WHERE workoutId = :workoutId")
    suspend fun clearExercises(workoutId: Int)

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY startDate DESC")
    fun getAllWorkouts(): Flow<List<WorkoutWithExercises>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    suspend fun getWorkoutById(workoutId: Int): WorkoutWithExercises?

    @Transaction
    suspend fun saveWorkout(workout: WorkoutEntity, exerciseIds: List<Int>) {
        val id = if (workout.id == 0) insert(workout).toInt() else {
            update(workout)
            workout.id
        }
        clearExercises(id)
        if (exerciseIds.isNotEmpty()) {
            insertCrossRefs(exerciseIds.map { WorkoutExerciseCrossRef(id, it) })
        }
    }
}
