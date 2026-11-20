package com.nmph.setly.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseCrossRef::class,
        ProgressEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun progressDao(): ProgressDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `progress` (
                        `id` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `exerciseId` INTEGER NOT NULL,
                        `completedReps` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        // Пересоздаём exercises без старых колонок: DROP COLUMN недоступен на minSdk 29.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `exercises_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `targetReps` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT INTO `exercises_new` (`id`, `name`, `targetReps`) " +
                        "SELECT `id`, `name`, `targetReps` FROM `exercises`",
                )
                db.execSQL("DROP TABLE `exercises`")
                db.execSQL("ALTER TABLE `exercises_new` RENAME TO `exercises`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workouts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `startDate` INTEGER NOT NULL,
                        `periodDays` INTEGER NOT NULL,
                        `sleepStart` TEXT NOT NULL,
                        `sleepEnd` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workout_exercise_cross_ref` (
                        `workoutId` INTEGER NOT NULL,
                        `exerciseId` INTEGER NOT NULL,
                        PRIMARY KEY(`workoutId`, `exerciseId`)
                    )
                    """.trimIndent(),
                )
            }
        }

        // completedReps (Int) заменяется на completedSets (CSV-строка). Старые счётчики
        // не переносятся осмысленно в подходы, поэтому записи начинаются с пустого списка.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `progress_new` (
                        `id` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `exerciseId` INTEGER NOT NULL,
                        `completedSets` TEXT NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT INTO `progress_new` (`id`, `date`, `exerciseId`, `completedSets`, `isCompleted`) " +
                        "SELECT `id`, `date`, `exerciseId`, '', `isCompleted` FROM `progress`",
                )
                db.execSQL("DROP TABLE `progress`")
                db.execSQL("ALTER TABLE `progress_new` RENAME TO `progress`")
            }
        }

        // Добавляем колонку с дефолтом: существующие тренировки получат отдых 30 минут.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `workouts` ADD COLUMN `restTimeMinutes` INTEGER NOT NULL DEFAULT 30",
                )
            }
        }

        // Синглтон на applicationContext: БД живёт дольше Activity и не утекает при повороте экрана.
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "setly_database",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
