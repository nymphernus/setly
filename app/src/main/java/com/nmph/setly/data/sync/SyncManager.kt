package com.nmph.setly.data.sync

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.nmph.setly.data.local.AppDatabase
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.ProgressEntity
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.data.local.WorkoutExerciseCrossRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Offline-first синхронизация: Room остаётся источником данных, Firestore — бэкап.
 * Пути: users/{uid}/workouts/{workoutId}/exercises/{exerciseId} и users/{uid}/progress/{date_exerciseId}.
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
                "autoRestTime" to workout.autoRestTime,
            ),
            SetOptions.merge(),
        ).await()

        exercises.forEach { exercise ->
            workoutRef.collection("exercises").document(exercise.id.toString())
                .set(
                    mapOf(
                        "name" to exercise.name,
                        "targetReps" to exercise.targetReps,
                        "repsPerSet" to exercise.repsPerSet,
                    ),
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
                    repsPerSet = (exData["repsPerSet"] as? Number)?.toInt() ?: 20,
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
                        autoRestTime = data["autoRestTime"] as? Boolean ?: false,
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

    /** ID документа — тот же, что в Room: "YYYY-MM-DD_<exerciseId>". */
    suspend fun pushProgressToCloud(progress: ProgressEntity) {
        val uid = currentUid() ?: return
        val ref = progressRef(uid, progress.id)
        val remote = ref.get().await()
        val remoteSets = (remote.data?.get("completedSets") as? List<*>)?.size ?: 0
        // Не понижаем облако: при конфликте побеждает большее число выполненных подходов.
        if (remote.exists() && remoteSets > progress.completedSets.size) return

        ref.set(
            mapOf(
                "date" to progress.date,
                "exerciseId" to progress.exerciseId,
                "completedSets" to progress.completedSets,
                "isCompleted" to progress.isCompleted,
            ),
            SetOptions.merge(),
        ).await()
        markSynced()
    }

    suspend fun pullProgressFromCloud(startDate: String, endDate: String) {
        val uid = currentUid() ?: return
        val snapshot = progressCollection(uid)
            .whereGreaterThanOrEqualTo("date", startDate)
            .whereLessThanOrEqualTo("date", endDate)
            .get()
            .await()

        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val remote = toProgress(doc.id, data) ?: continue
            val local = database.progressDao().getProgressById(remote.id)
            // При равном числе подходов оставляем локальную запись.
            if (local != null && local.completedSets.size >= remote.completedSets.size) continue
            database.progressDao().insertOrUpdate(remote)
        }
        markSynced()
    }

    suspend fun pullProgressForCurrentMonth() {
        val now = LocalDate.now()
        pullProgressFromCloud(
            now.withDayOfMonth(1).toString(),
            now.withDayOfMonth(now.lengthOfMonth()).toString(),
        )
    }

    suspend fun pushAllProgressToCloud() {
        database.progressDao().getAllProgress().forEach { pushProgressToCloud(it) }
    }

    suspend fun pushAllToCloud() {
        val localWorkouts = database.workoutDao().getAllWorkouts().first()
        localWorkouts.forEach { workout ->
            pushWorkoutsToCloud(workout.workout, workout.exercises)
        }
        pushAllProgressToCloud()
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
            pullProgressForCurrentMonth()
            pushAllToCloud()
        } finally {
            _isSyncing.value = false
        }
    }

    private fun users() = firestore.collection("users")

    private fun progressCollection(uid: String) = users().document(uid).collection("progress")

    private fun progressRef(uid: String, id: String) = progressCollection(uid).document(id)

    private fun toProgress(id: String, data: Map<String, Any>): ProgressEntity? {
        val date = data["date"] as? String ?: return null
        val exerciseId = (data["exerciseId"] as? Number)?.toInt() ?: return null
        val completedSets = (data["completedSets"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
            ?: emptyList()
        return ProgressEntity(
            id = id,
            date = date,
            exerciseId = exerciseId,
            completedSets = completedSets,
            isCompleted = data["isCompleted"] as? Boolean ?: completedSets.isNotEmpty(),
        )
    }

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
