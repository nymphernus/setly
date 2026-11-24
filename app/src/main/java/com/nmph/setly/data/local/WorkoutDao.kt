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

    @Query("DELETE FROM workout_exercise_cross_ref WHERE exerciseId = :exerciseId")
    suspend fun deleteExerciseLinks(exerciseId: Int)

    @Query("SELECT * FROM workouts ORDER BY startDate DESC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>

    @Query(
        """
        SELECT cr.workoutId AS workoutId,
               e.id AS id,
               e.name AS name,
               e.description AS description,
               cr.repsPerSet AS repsPerSet,
               cr.dailyTargetReps AS dailyTargetReps
        FROM workout_exercise_cross_ref cr
        INNER JOIN exercises e ON e.id = cr.exerciseId
        ORDER BY cr.workoutId
        """,
    )
    fun observePlannedExercises(): Flow<List<PlannedExerciseRow>>

    @Transaction
    suspend fun saveWorkout(workout: WorkoutEntity, plans: List<WorkoutExerciseCrossRef>): Int {
        val id = if (workout.id == 0) {
            insert(workout).toInt()
        } else {
            update(workout)
            workout.id
        }
        clearExercises(id)
        if (plans.isNotEmpty()) {
            insertCrossRefs(plans.map { it.copy(workoutId = id) })
        }
        return id
    }
}
