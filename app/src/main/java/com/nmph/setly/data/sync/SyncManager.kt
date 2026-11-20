package com.nmph.setly.data.sync

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.nmph.setly.data.local.AppDatabase
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.data.local.WorkoutExerciseCrossRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Offline-first синхронизация: Room остаётся источником данных, Firestore — бэкап.
 * Путь: users/{uid}/workouts/{workoutId}/exercises/{exerciseId}.
 */
class SyncManager(
    context: Context,
    private val firestore: FirebaseFirestore,
) {
    private val database = AppDatabase.getDatabase(context.applicationContext)
    private val auth = FirebaseAuth.getInstance()
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _lastSyncAt = MutableStateFlow(
        if (prefs.contains(KEY_LAST_SYNC)) prefs.getLong(KEY_LAST_SYNC, 0L) else null,
    )
    val lastSyncAt: StateFlow<Long?> = _lastSyncAt

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    val isSignedIn: Boolean get() = auth.currentUser != null

    suspend fun pushWorkoutsToCloud(workout: WorkoutEntity, exercises: List<ExerciseEntity>) {
        val uid = currentUid() ?: return
        val workoutRef = users().document(uid)
            .collection("workouts")
            .document(workout.id.toString())

        workoutRef.set(
            mapOf(
                "name" to workout.name,
                "startDate" to workout.startDate,
                "periodDays" to workout.periodDays,
                "sleepStart" to workout.sleepStart,
                "sleepEnd" to workout.sleepEnd,
                "restTimeMinutes" to workout.restTimeMinutes,
            ),
            SetOptions.merge(),
        ).await()

        exercises.forEach { exercise ->
            workoutRef.collection("exercises").document(exercise.id.toString())
                .set(
                    mapOf("name" to exercise.name, "targetReps" to exercise.targetReps),
                    SetOptions.merge(),
                ).await()
        }
        markSynced()
    }

    // Облако перезаписывает локальные записи с тем же ID (упрощённая стратегия конфликтов).
    suspend fun pullFromCloud() {
        val uid = currentUid() ?: return
        val snapshots = users().document(uid).collection("workouts").get().await()

        for (doc in snapshots.documents) {
            val workoutId = doc.id.toIntOrNull() ?: continue
            val data = doc.data ?: continue
            val name = data["name"] as? String ?: continue
            val startDate = (data["startDate"] as? Number)?.toLong() ?: continue

            val exerciseSnapshots = doc.reference.collection("exercises").get().await()
            val exercises = exerciseSnapshots.documents.mapNotNull { exDoc ->
                val exId = exDoc.id.toIntOrNull() ?: return@mapNotNull null
                val exData = exDoc.data ?: return@mapNotNull null
                ExerciseEntity(
                    id = exId,
                    name = exData["name"] as? String ?: return@mapNotNull null,
                    targetReps = (exData["targetReps"] as? Number)?.toInt() ?: 0,
                )
            }

            database.exerciseDao().let { dao ->
                exercises.forEach { dao.insert(it) }
            }
            database.workoutDao().run {
                insert(
                    WorkoutEntity(
                        id = workoutId,
                        name = name,
                        startDate = startDate,
                        periodDays = (data["periodDays"] as? Number)?.toInt() ?: 7,
                        sleepStart = data["sleepStart"] as? String ?: "21:00",
                        sleepEnd = data["sleepEnd"] as? String ?: "08:00",
                        restTimeMinutes = (data["restTimeMinutes"] as? Number)?.toInt() ?: 30,
                    ),
                )
                clearExercises(workoutId)
                if (exercises.isNotEmpty()) {
                    insertCrossRefs(exercises.map { WorkoutExerciseCrossRef(workoutId, it.id) })
                }
            }
        }
        markSynced()
    }

    suspend fun pushAllToCloud() {
        val localWorkouts = database.workoutDao().getAllWorkouts().first()
        localWorkouts.forEach { workout ->
            pushWorkoutsToCloud(workout.workout, workout.exercises)
        }
    }

    suspend fun deleteWorkoutFromCloud(workoutId: Int) {
        val uid = currentUid() ?: return
        val workoutRef = users().document(uid).collection("workouts").document(workoutId.toString())
        val exercises = workoutRef.collection("exercises").get().await()
        firestore.batch().apply {
            exercises.documents.forEach { delete(it.reference) }
            delete(workoutRef)
        }.commit().await()
        markSynced()
    }

    suspend fun syncNow() {
        _isSyncing.value = true
        try {
            pullFromCloud()
            pushAllToCloud()
        } finally {
            _isSyncing.value = false
        }
    }

    private fun users() = firestore.collection("users")

    private fun currentUid(): String? = auth.currentUser?.uid

    private fun markSynced() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
        _lastSyncAt.value = now
    }

    private companion object {
        const val PREFS_NAME = "setly_sync"
        const val KEY_LAST_SYNC = "last_sync_at"
    }
}
