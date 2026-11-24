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

        // Текущая тренировка — активная сегодня. Если её нет (или нет тренировок вовсе),
        // уведомление ведёт на главный экран.
        val workoutId = currentWorkoutId()
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

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Setly")
            .setContentText(if (workoutId != null) "Пора делать подход!" else "Пора тренироваться!")
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

    private fun currentWorkoutId(): Int? = try {
        runBlocking {
            val workouts = AppDatabase.getDatabase(applicationContext)
                .workoutDao()
                .getAllWorkouts()
                .first()
            val today = LocalDate.now()
            val active = workouts.firstOrNull { workout ->
                val start = Instant.ofEpochMilli(workout.startDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                !today.isBefore(start) &&
                    !today.isAfter(start.plusDays(workout.periodDays.toLong() - 1))
            }
            active?.id?.takeIf { it > 0 }
        }
    } catch (e: Exception) {
        null
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

    private companion object {
        const val CHANNEL_ID = "setly_reminders"
        const val NOTIFICATION_ID = 1001
        const val ACTION_OPEN_FROM_NOTIFICATION = "com.nmph.setly.OPEN_WORKOUT"
        const val EXTRA_WORKOUT_ID = "workoutId"
    }
}
