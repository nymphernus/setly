package com.nmph.setly.data.sync

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.nmph.setly.data.local.AppDatabase
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.ProgressEntity
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.data.local.WorkoutExerciseCrossRef
import com.nmph.setly.data.local.WorkoutPlan
import com.nmph.setly.data.local.withPlans
import com.nmph.setly.utils.DistributionHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.time.LocalDate

/**
 * Offline-first синхронизация: Room остаётся источником данных, Firestore — бэкап.
 * Пути: users/{uid}/workouts/{workoutId}/exercises/{exerciseId} и users/{uid}/progress/{date_exerciseId}.
 *
 * Любая операция с облаком ограничена 10 секундами: сетевой вызов больше не может
 * оставить интерфейс в состоянии «синхронизируемся» навсегда.
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

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError

    val isSignedIn: Boolean get() = auth.currentUser != null

    suspend fun pushWorkoutToCloud(plan: WorkoutPlan) {
        val uid = currentUid() ?: return
        val workout = plan.workout
        val workoutRef = users().document(uid)
            .collection("workouts")
            .document(workout.id.toString())

        firestoreCall {
            workoutRef.set(
                mapOf(
                    "name" to workout.name,
                    "startDate" to workout.startDate,
                    "periodDays" to workout.periodDays,
                    "sleepStart" to workout.sleepStart,
                    "sleepEnd" to workout.sleepEnd,
                ),
                SetOptions.merge(),
            ).await()
        }

        plan.exercises.forEach { planned ->
            firestoreCall {
                workoutRef.collection("exercises").document(planned.exercise.id.toString())
                    .set(
                        mapOf(
                            "name" to planned.exercise.name,
                            "description" to planned.exercise.description,
                            "repsPerSet" to planned.repsPerSet,
                            "dailyTargetReps" to planned.dailyTargetReps,
                        ),
                        SetOptions.merge(),
                    ).await()
            }
        }
        markSynced()
    }

    // Облако перезаписывает локальные записи с тем же ID (упрощённая стратегия конфликтов).
    suspend fun pullFromCloud() {
        val uid = currentUid() ?: return
        val snapshots = firestoreCall {
            users().document(uid).collection("workouts").get().await()
        }

        for (doc in snapshots.documents) {
            val workoutId = doc.id.toIntOrNull() ?: continue
            val data = doc.data ?: continue
            val name = data["name"] as? String ?: continue
            val startDate = (data["startDate"] as? Number)?.toLong() ?: continue

            val exerciseSnapshots = firestoreCall {
                doc.reference.collection("exercises").get().await()
            }
            val planned = exerciseSnapshots.documents.mapNotNull { exDoc ->
                val exId = exDoc.id.toIntOrNull() ?: return@mapNotNull null
                val exData = exDoc.data ?: return@mapNotNull null
                val exercise = ExerciseEntity(
                    id = exId,
                    name = exData["name"] as? String ?: return@mapNotNull null,
                    description = exData["description"] as? String ?: "",
                )
                val dailyTargetReps = (exData["dailyTargetReps"] as? Number)?.toInt() ?: 0
                val repsPerSet = (exData["repsPerSet"] as? Number)?.toInt()
                    ?: DistributionHelper.DEFAULT_REPS_PER_SET
                Triple(exercise, dailyTargetReps, repsPerSet)
            }

            database.exerciseDao().let { dao ->
                planned.forEach { dao.insert(it.first) }
            }
            database.workoutDao().run {
                insert(
                    WorkoutEntity(
                        id = workoutId,
                        name = name,
                        startDate = startDate,
                        periodDays = (data["periodDays"] as? Number)?.toInt() ?: 30,
                        sleepStart = data["sleepStart"] as? String ?: "22:00",
                        sleepEnd = data["sleepEnd"] as? String ?: "08:00",
                    ),
                )
                clearExercises(workoutId)
                if (planned.isNotEmpty()) {
                    insertCrossRefs(planned.map { (exercise, dailyTargetReps, repsPerSet) ->
                        WorkoutExerciseCrossRef(
                            workoutId,
                            exercise.id,
                            dailyTargetReps,
                            repsPerSet,
                        )
                    })
                }
            }
        }
        markSynced()
    }

    /** ID документа — тот же, что в Room: "YYYY-MM-DD_<exerciseId>". */
    suspend fun pushProgressToCloud(progress: ProgressEntity) {
        val uid = currentUid() ?: return
        val ref = progressRef(uid, progress.id)
        val remote = firestoreCall { ref.get().await() }
        val remoteSets = (remote.data?.get("completedSets") as? List<*>)?.size ?: 0
        // Не понижаем облако: при конфликте побеждает большее число выполненных подходов.
        if (remote.exists() && remoteSets > progress.completedSets.size) return

        firestoreCall {
            ref.set(
                mapOf(
                    "date" to progress.date,
                    "exerciseId" to progress.exerciseId,
                    "completedSets" to progress.completedSets,
                    "isCompleted" to progress.isCompleted,
                ),
                SetOptions.merge(),
            ).await()
        }
        markSynced()
    }

    suspend fun pullProgressFromCloud(startDate: String, endDate: String) {
        val uid = currentUid() ?: return
        val snapshot = firestoreCall {
            progressCollection(uid)
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .get()
                .await()
        }

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
        val workoutEntities = database.workoutDao().getAllWorkouts().first()
        val rows = database.workoutDao().observePlannedExercises().first()
        workoutEntities.withPlans(rows).forEach { plan ->
            pushWorkoutToCloud(plan)
        }
        pushAllProgressToCloud()
    }

    suspend fun deleteWorkoutFromCloud(workoutId: Int) {
        val uid = currentUid() ?: return
        val workoutRef = users().document(uid).collection("workouts").document(workoutId.toString())
        val exercises = firestoreCall { workoutRef.collection("exercises").get().await() }
        firestoreCall {
            firestore.batch().apply {
                exercises.documents.forEach { delete(it.reference) }
                delete(workoutRef)
            }.commit().await()
        }
        markSynced()
    }

    /** Возвращает true, если все шаги завершились без ошибок. */
    suspend fun syncNow(): Boolean {
        _isSyncing.value = true
        _lastSyncError.value = null
        return try {
            withTimeout(SYNC_TIMEOUT_MS) {
                pullFromCloud()
                pullProgressForCurrentMonth()
                pushAllToCloud()
            }
            _lastSyncError.value == null
        } catch (e: TimeoutCancellationException) {
            _lastSyncError.value = "Синхронизация не успела за 10 секунд"
            false
        } catch (e: CancellationException) {
            false
        } catch (e: Exception) {
            if (_lastSyncError.value == null) _lastSyncError.value = describeError(e)
            false
        } finally {
            _isSyncing.value = false
        }
    }

    /** Очищает сообщение об ошибке, чтобы старый текст не висел после успешного входа. */
    fun clearError() {
        _lastSyncError.value = null
    }

    /** Одна Firestore-операция с жёстким лимитом времени и записью ошибки в состояние. */
    private suspend fun <T> firestoreCall(block: suspend () -> T): T = try {
        withTimeout(SYNC_TIMEOUT_MS) { block() }
    } catch (e: TimeoutCancellationException) {
        _lastSyncError.value = "Ошибка сети: ответ не получен за 10 секунд"
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        _lastSyncError.value = describeError(e)
        throw e
    }

    private fun describeError(error: Exception): String = when (error) {
        is FirebaseFirestoreException -> when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Нет прав доступа к облаку"
            FirebaseFirestoreException.Code.UNAVAILABLE -> "Ошибка сети: облако недоступно"
            else -> "Ошибка Firestore: ${error.code.name}"
        }
        is IOException -> "Ошибка сети: проверьте подключение"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Ошибка синхронизации"
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
        const val SYNC_TIMEOUT_MS = 10_000L
        const val PREFS_NAME = "setly_sync"
        const val KEY_LAST_SYNC = "last_sync_at"
    }
}
