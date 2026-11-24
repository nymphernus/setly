package com.nmph.setly

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.nmph.setly.data.local.AppDatabase
import com.nmph.setly.data.local.WorkoutPlan
import com.nmph.setly.data.local.withPlans
import com.nmph.setly.utils.DistributionHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class NotificationWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        createChannel()

        // ID тренировки приходит из настроек; без него берём активную на сегодня.
        val requestedId = inputData.getInt(KEY_WORKOUT_ID, -1).takeIf { it > 0 }
        val remindBefore = inputData.getInt(KEY_REMIND_BEFORE_MINUTES, 5).coerceAtLeast(1)
        val reminder = loadReminder(requestedId)
        val workoutId = reminder?.first?.workout?.id
        val deepLink = if (workoutId != null) {
            "setly://workout_detail/$workoutId"
        } else {
            "setly://home"
        }
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = ACTION_OPEN_FROM_NOTIFICATION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse(deepLink)
            if (workoutId != null) putExtra(EXTRA_WORKOUT_ID, workoutId)
        }
        val pendingIntent: PendingIntent = TaskStackBuilder.create(applicationContext).run {
            addNextIntentWithParentStack(openIntent)
            getPendingIntent(
                workoutId ?: 0,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )!!
        }

        val message = if (reminder != null) {
            val plan = reminder.first
            "Скоро подход! Тренировка \"${plan.workout.name}\", осталось ${reminder.second} кругов"
        } else {
            "Скоро подход! Пора тренироваться"
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Setly")
            .setContentText(message)
            .setSubText("Напоминание за $remindBefore мин")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Без разрешения на Android 13+ notify() бросит SecurityException.
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        if (canNotify) {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        }
        return Result.success()
    }

    /** Возвращает план и число оставшихся подходов на сегодня. */
    private fun loadReminder(requestedId: Int?): Pair<WorkoutPlan, Int>? = try {
        runBlocking {
            val database = AppDatabase.getDatabase(applicationContext)
            val workoutDao = database.workoutDao()
            val plans = workoutDao.getAllWorkouts().first()
                .withPlans(workoutDao.observePlannedExercises().first())
            val plan = requestedId?.let { id ->
                plans.firstOrNull { it.workout.id == id }
            } ?: plans.firstOrNull { isActiveToday(it.workout) }
            if (plan == null) return@runBlocking null

            val setsByExercise = plan.exercises.associate { planned ->
                planned.exercise.id to DistributionHelper.setsForPlan(planned, plan.workout)
            }
            val exerciseIds = setsByExercise.keys
            val progress = database.progressDao()
                .getProgressForDate(LocalDate.now().toString())
                .first()
            val completed = progress
                .filter { it.exerciseId in exerciseIds }
                .sumOf { record ->
                    val sets = setsByExercise[record.exerciseId].orEmpty()
                    record.completedSets.count { index -> index in sets.indices }
                }
            val total = setsByExercise.values.sumOf { it.size }
            plan to (total - completed).coerceAtLeast(0)
        }
    } catch (e: Exception) {
        null
    }

    private fun isActiveToday(workout: com.nmph.setly.data.local.WorkoutEntity): Boolean {
        val today = LocalDate.now()
        val start = Instant.ofEpochMilli(workout.startDate)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return !today.isBefore(start) &&
            !today.isAfter(start.plusDays(workout.periodDays.toLong() - 1))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Напоминания Setly",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Напоминания о тренировке" }
            NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)
        }
    }

    companion object {
        const val KEY_WORKOUT_ID = "workoutId"
        const val KEY_REMIND_BEFORE_MINUTES = "remindBeforeMinutes"
        const val CHANNEL_ID = "setly_reminders"
        const val NOTIFICATION_ID = 1001
        const val ACTION_OPEN_FROM_NOTIFICATION = "com.nmph.setly.OPEN_WORKOUT"
        const val EXTRA_WORKOUT_ID = "workoutId"
    }
}
