package com.nmph.setly.data.sync

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.nmph.setly.data.local.AppDatabase
import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.PlannedExercise
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Короткая запись о бекапе для списка в профиле. */
data class BackupInfo(
    val id: String,
    val createdAt: Long,
) {
    val label: String get() = Instant.ofEpochMilli(createdAt)
        .atZone(ZoneId.systemDefault())
        .format(BACKUP_FORMAT)

    private companion object {
        val BACKUP_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("ru"))
    }
}

private data class RemotePlanned(
    val workoutId: Int,
    val exercise: ExerciseEntity,
    val dailyTargetReps: Int,
    val repsPerSet: Int,
)

private data class MergedRef(
    val workoutId: Int,
    val exerciseId: Int,
    val dailyTargetReps: Int,
    val repsPerSet: Int,
)

/**
 * Offline-first синхронизация: Room остаётся источником данных, Firestore — бэкап.
 * Пути: users/{uid}/workouts/{workoutId}/exercises/{exerciseId},
 * users/{uid}/progress/{date_exerciseId} и users/{uid}/backups/{timestamp}.
 *
 * Обычная синхронизация — слияние по ID: отсутствующая запись копируется в другую
 * сторону, а при конфликте выбирается более полная (больше completedSets; для
 * тренировки — более поздняя дата). Любая операция с облаком ограничена 10 секундами.
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
                    "notificationsEnabled" to workout.notificationsEnabled,
                    "remindBeforeMinutes" to workout.remindBeforeMinutes,
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
                            "category" to planned.exercise.category,
                            "unit" to planned.exercise.unit,
                            "iconResName" to planned.exercise.iconResName,
                            "repsPerSet" to planned.repsPerSet,
                            "dailyTargetReps" to planned.dailyTargetReps,
                        ),
                        SetOptions.merge(),
                    ).await()
            }
        }
        markSynced()
    }

    /** Прямое чтение облака без слияния: используется при входе в аккаунт. */
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
                toRemotePlanned(workoutId, exDoc.id, exDoc.data ?: return@mapNotNull null)
            }

            database.exerciseDao().let { dao ->
                planned.forEach { dao.insert(it.exercise) }
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
                        notificationsEnabled = data["notificationsEnabled"] as? Boolean ?: true,
                        remindBeforeMinutes = (data["remindBeforeMinutes"] as? Number)?.toInt()
                            ?: WorkoutEntity.DEFAULT_REMIND_BEFORE_MINUTES,
                    ),
                )
                clearExercises(workoutId)
                if (planned.isNotEmpty()) {
                    insertCrossRefs(planned.map { it.toCrossRef() })
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

    /** Полный снимок локальных данных в users/{uid}/backups/{timestamp}. */
    suspend fun createBackup(): BackupInfo {
        val uid = currentUid() ?: error("Войдите в аккаунт, чтобы создать бекап")
        val createdAt = System.currentTimeMillis()
        val exercises = database.exerciseDao().getAllExercises().first()
        val workouts = database.workoutDao().getAllWorkouts().first()
        val crossRefs = database.workoutDao().observePlannedExercises().first()
        val progress = database.progressDao().getAllProgress()

        val data = mapOf(
            "createdAt" to createdAt,
            "exercises" to exercises.map { exercise ->
                mapOf(
                    "id" to exercise.id,
                    "name" to exercise.name,
                    "category" to exercise.category,
                    "unit" to exercise.unit,
                    "iconResName" to exercise.iconResName,
                )
            },
            "workouts" to workouts.map { it.toBackupMap() },
            "crossRefs" to crossRefs.map { row ->
                mapOf(
                    "workoutId" to row.workoutId,
                    "exerciseId" to row.exercise.id,
                    "dailyTargetReps" to row.dailyTargetReps,
                    "repsPerSet" to row.repsPerSet,
                )
            },
            "progress" to progress.map { it.toBackupMap() },
        )
        firestoreCall {
            users().document(uid).collection("backups").document(createdAt.toString())
                .set(data).await()
        }
        markSynced()
        return BackupInfo(createdAt.toString(), createdAt)
    }

    /** Список доступных бекапов, сначала самые свежие. */
    suspend fun listBackups(): List<BackupInfo> {
        val uid = currentUid() ?: return emptyList()
        val snapshot = firestoreCall {
            users().document(uid).collection("backups")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()
        }
        return snapshot.documents.mapNotNull { doc ->
            val createdAt = doc.getLong("createdAt") ?: doc.id.toLongOrNull() ?: return@mapNotNull null
            BackupInfo(doc.id, createdAt)
        }
    }

    /** Полная перезапись локальных данных выбранным бекапом. */
    suspend fun restoreBackup(backupId: String) {
        val uid = currentUid() ?: error("Войдите в аккаунт, чтобы восстановить бекап")
        val doc = firestoreCall {
            users().document(uid).collection("backups").document(backupId).get().await()
        }
        if (!doc.exists()) error("Бекап не найден")
        val data = doc.data ?: error("Бекап пуст")

        val exercises = asMapList(data["exercises"]).mapNotNull(::toExercise)
        val workouts = asMapList(data["workouts"]).mapNotNull(::toWorkout)
        val crossRefs = asMapList(data["crossRefs"]).mapNotNull(::toCrossRef)
        val progress = asMapList(data["progress"]).mapNotNull { item ->
            val id = item["id"] as? String ?: return@mapNotNull null
            toProgress(id, item)
        }
        if (exercises.isEmpty() && workouts.isEmpty() && progress.isEmpty()) {
            error("В бекапе нет данных")
        }

        localReplace(exercises, workouts, crossRefs, progress)
        markSynced()
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

    /**
     * Обычная синхронизация: объединяем локальные и облачные записи по ID,
     * затем отправляем результат обратно в облако.
     */
    suspend fun mergeWithCloud() {
        val uid = currentUid() ?: return

        val localExercises = database.exerciseDao().getAllExercises().first()
        val localWorkouts = database.workoutDao().getAllWorkouts().first()
        val localRows = database.workoutDao().observePlannedExercises().first()
        val localProgress = database.progressDao().getAllProgress()

        val remoteWorkouts = firestoreCall {
            users().document(uid).collection("workouts").get().await()
        }.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val id = doc.id.toIntOrNull() ?: return@mapNotNull null
            val workout = toWorkout(data + mapOf("id" to id)) ?: return@mapNotNull null
            val planned = firestoreCall { doc.reference.collection("exercises").get().await() }
                .documents.mapNotNull { exDoc ->
                    toRemotePlanned(id, exDoc.id, exDoc.data ?: return@mapNotNull null)
                }
            workout to planned
        }
        val remotePlans = remoteWorkouts.flatMap { (workout, planned) ->
            planned.map { it.copy(workoutId = workout.id) }
        }
        val remoteProgress = firestoreCall { progressCollection(uid).get().await() }
            .documents.mapNotNull { doc -> toProgress(doc.id, doc.data ?: return@mapNotNull null) }

        // Тренировки: если ID есть в двух местах, берём более свежую/длинную по дате.
        val workouts = localWorkouts.associateBy { it.id }.toMutableMap()
        remoteWorkouts.forEach { (remote, _) ->
            val local = workouts[remote.id]
            if (local == null || preferRemoteWorkout(remote, local)) workouts[remote.id] = remote
        }

        // Упражнения: локальные данные пользователя имеют приоритет, нет — добавляем из облака.
        val exercises = localExercises.associateBy { it.id }.toMutableMap()
        remotePlans.forEach { planned ->
            exercises.putIfAbsent(planned.exercise.id, planned.exercise)
        }

        // Связи тренировка → упражнение: union по паре ID, конфликт решает большая норма.
        val refs = linkedMapOf<Pair<Int, Int>, MergedRef>()
        localRows.forEach { row ->
            val key = row.workoutId to row.exercise.id
            refs[key] = MergedRef(
                row.workoutId, row.exercise.id, row.dailyTargetReps, row.repsPerSet,
            )
        }
        remotePlans.forEach { planned ->
            if (planned.workoutId !in workouts) return@forEach
            val key = planned.workoutId to planned.exercise.id
            val candidate = MergedRef(
                planned.workoutId, planned.exercise.id,
                planned.dailyTargetReps, planned.repsPerSet,
            )
            val current = refs[key]
            if (current == null ||
                candidate.dailyTargetReps + candidate.repsPerSet >
                    current.dailyTargetReps + current.repsPerSet
            ) {
                refs[key] = candidate
            }
        }

        // Прогресс: при конфликте побеждает больше выполненных подходов, затем позже дата.
        val progress = localProgress.associateBy { it.id }.toMutableMap()
        remoteProgress.forEach { remote ->
            val local = progress[remote.id]
            if (local == null || preferRemoteProgress(remote, local)) progress[remote.id] = remote
        }

        val crossRefs = refs.values.map {
            WorkoutExerciseCrossRef(
                it.workoutId, it.exerciseId, it.dailyTargetReps, it.repsPerSet,
            )
        }
        localReplace(exercises.values.toList(), workouts.values.toList(), crossRefs, progress.values.toList())

        // Распространяем объединённое состояние обратно в облако.
        workouts.values.forEach { workout ->
            val plan = WorkoutPlan(
                workout = workout,
                exercises = crossRefs.filter { it.workoutId == workout.id }.mapNotNull { ref ->
                    exercises[ref.exerciseId]?.let { exercise ->
                        PlannedExercise(exercise, ref.dailyTargetReps, ref.repsPerSet)
                    }
                },
            )
            pushWorkoutToCloud(plan)
        }
        progress.values.forEach { pushProgressToCloud(it) }
        markSynced()
    }

    /** Возвращает true, если все шаги завершились без ошибок. */
    suspend fun syncNow(): Boolean {
        _isSyncing.value = true
        _lastSyncError.value = null
        return try {
            withTimeout(SYNC_TIMEOUT_MS) {
                mergeWithCloud()
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

    private fun preferRemoteWorkout(remote: WorkoutEntity, local: WorkoutEntity): Boolean =
        remote.startDate > local.startDate ||
            (remote.startDate == local.startDate && remote.periodDays > local.periodDays)

    private fun preferRemoteProgress(remote: ProgressEntity, local: ProgressEntity): Boolean =
        when {
            remote.completedSets.size != local.completedSets.size ->
                remote.completedSets.size > local.completedSets.size
            else -> remote.date > local.date
        }

    private suspend fun localReplace(
        exercises: List<ExerciseEntity>,
        workouts: List<WorkoutEntity>,
        crossRefs: List<WorkoutExerciseCrossRef>,
        progress: List<ProgressEntity>,
    ) {
        database.progressDao().clearAllProgress()
        database.workoutDao().clearAllCrossRefs()
        database.workoutDao().clearAllWorkouts()
        database.exerciseDao().clearAllExercises()

        exercises.forEach { database.exerciseDao().insert(it) }
        workouts.forEach { database.workoutDao().insert(it) }
        if (crossRefs.isNotEmpty()) database.workoutDao().insertCrossRefs(crossRefs)
        progress.forEach { database.progressDao().insertOrUpdate(it) }
    }

    private fun toRemotePlanned(
        workoutId: Int,
        exerciseIdText: String,
        data: Map<String, Any>,
    ): RemotePlanned? {
        val id = exerciseIdText.toIntOrNull() ?: return null
        val exercise = ExerciseEntity(
            id = id,
            name = data["name"] as? String ?: return null,
            category = data["category"] as? String ?: ExerciseEntity.DEFAULT_CATEGORY,
            unit = data["unit"] as? String ?: ExerciseEntity.DEFAULT_UNIT,
            iconResName = data["iconResName"] as? String ?: ExerciseEntity.DEFAULT_ICON,
        )
        return RemotePlanned(
            workoutId = workoutId,
            exercise = exercise,
            dailyTargetReps = (data["dailyTargetReps"] as? Number)?.toInt() ?: 0,
            repsPerSet = (data["repsPerSet"] as? Number)?.toInt()
                ?: DistributionHelper.DEFAULT_REPS_PER_SET,
        )
    }

    private fun RemotePlanned.toCrossRef() = WorkoutExerciseCrossRef(
        workoutId, exercise.id, dailyTargetReps, repsPerSet,
    )

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

    private fun WorkoutEntity.toBackupMap() = mapOf(
        "id" to id,
        "name" to name,
        "startDate" to startDate,
        "periodDays" to periodDays,
        "sleepStart" to sleepStart,
        "sleepEnd" to sleepEnd,
        "notificationsEnabled" to notificationsEnabled,
        "remindBeforeMinutes" to remindBeforeMinutes,
    )

    private fun ProgressEntity.toBackupMap() = mapOf(
        "id" to id,
        "date" to date,
        "exerciseId" to exerciseId,
        "completedSets" to completedSets,
        "isCompleted" to isCompleted,
    )

    private fun asMapList(value: Any?): List<Map<String, Any>> =
        (value as? List<*>)?.mapNotNull { it as? Map<String, Any> }.orEmpty()

    private fun toExercise(data: Map<String, Any>): ExerciseEntity? {
        val id = (data["id"] as? Number)?.toInt() ?: return null
        val name = data["name"] as? String ?: return null
        return ExerciseEntity(
            id = id,
            name = name,
            category = data["category"] as? String ?: ExerciseEntity.DEFAULT_CATEGORY,
            unit = data["unit"] as? String ?: ExerciseEntity.DEFAULT_UNIT,
            iconResName = data["iconResName"] as? String ?: ExerciseEntity.DEFAULT_ICON,
        )
    }

    private fun toWorkout(data: Map<String, Any>): WorkoutEntity? {
        val id = (data["id"] as? Number)?.toInt() ?: return null
        val name = data["name"] as? String ?: return null
        val startDate = (data["startDate"] as? Number)?.toLong() ?: return null
        return WorkoutEntity(
            id = id,
            name = name,
            startDate = startDate,
            periodDays = (data["periodDays"] as? Number)?.toInt() ?: 30,
            sleepStart = data["sleepStart"] as? String ?: "22:00",
            sleepEnd = data["sleepEnd"] as? String ?: "08:00",
            notificationsEnabled = data["notificationsEnabled"] as? Boolean ?: true,
            remindBeforeMinutes = (data["remindBeforeMinutes"] as? Number)?.toInt()
                ?: WorkoutEntity.DEFAULT_REMIND_BEFORE_MINUTES,
        )
    }

    private fun toCrossRef(data: Map<String, Any>): WorkoutExerciseCrossRef? {
        val workoutId = (data["workoutId"] as? Number)?.toInt() ?: return null
        val exerciseId = (data["exerciseId"] as? Number)?.toInt() ?: return null
        return WorkoutExerciseCrossRef(
            workoutId = workoutId,
            exerciseId = exerciseId,
            dailyTargetReps = (data["dailyTargetReps"] as? Number)?.toInt() ?: 0,
            repsPerSet = (data["repsPerSet"] as? Number)?.toInt()
                ?: DistributionHelper.DEFAULT_REPS_PER_SET,
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
