package com.nmph.setly.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nmph.setly.data.AchievementRepository

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseCrossRef::class,
        ProgressEntity::class,
        AchievementEntity::class,
    ],
    version = 13,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun progressDao(): ProgressDao
    abstract fun achievementDao(): AchievementDao

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

        // Новые поля: ручной/авто отдых у тренировки и размер подхода у упражнения.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `workouts` ADD COLUMN `autoRestTime` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE `exercises` ADD COLUMN `repsPerSet` INTEGER NOT NULL DEFAULT 20",
                )
            }
        }

        // v7: меняется семантика progress.completedSets — теперь это индексы подходов,
        // а не values повторений. Старые отметки больше не соответствуют новым спискам,
        // поэтому очищаем только прогресс; тренировки и упражнения сохраняются.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE `progress` SET `completedSets` = '', `isCompleted` = 0")
            }
        }

        // v8: цель становится дневной и живёт в связи тренировка↔упражнение.
        // Старый targetReps был общим за период, поэтому пересчитываем его в дневную норму:
        // ceil(targetReps / periodDays). Ручной/авто отдых больше не хранится — он всегда авто.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `workout_exercise_cross_ref_new` (
                        `workoutId` INTEGER NOT NULL,
                        `exerciseId` INTEGER NOT NULL,
                        `dailyTargetReps` INTEGER NOT NULL,
                        PRIMARY KEY(`workoutId`, `exerciseId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `workout_exercise_cross_ref_new`
                        (`workoutId`, `exerciseId`, `dailyTargetReps`)
                    SELECT cr.`workoutId`, cr.`exerciseId`,
                           CASE WHEN w.`periodDays` > 0 AND e.`targetReps` > 0
                                THEN (e.`targetReps` + w.`periodDays` - 1) / w.`periodDays`
                                ELSE 0 END
                    FROM `workout_exercise_cross_ref` cr
                    INNER JOIN `workouts` w ON w.`id` = cr.`workoutId`
                    INNER JOIN `exercises` e ON e.`id` = cr.`exerciseId`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `workout_exercise_cross_ref`")
                db.execSQL(
                    "ALTER TABLE `workout_exercise_cross_ref_new` RENAME TO `workout_exercise_cross_ref`",
                )

                db.execSQL(
                    """
                    CREATE TABLE `exercises_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `repsPerSet` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `exercises_new` (`id`, `name`, `repsPerSet`)
                    SELECT `id`, `name`, `repsPerSet` FROM `exercises`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `exercises`")
                db.execSQL("ALTER TABLE `exercises_new` RENAME TO `exercises`")

                db.execSQL(
                    """
                    CREATE TABLE `workouts_new` (
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
                    INSERT INTO `workouts_new` (`id`, `name`, `startDate`, `periodDays`, `sleepStart`, `sleepEnd`)
                    SELECT `id`, `name`, `startDate`, `periodDays`, `sleepStart`, `sleepEnd` FROM `workouts`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `workouts`")
                db.execSQL("ALTER TABLE `workouts_new` RENAME TO `workouts`")

                // Число подходов изменилось — старые отметки больше не соответствуют чипсам.
                db.execSQL("UPDATE `progress` SET `completedSets` = '', `isCompleted` = 0")
            }
        }

        // v9: размер подхода переезжает из упражнения в тренировку, у упражнения появляется
        // описание. Старый repsPerSet переносится в каждую связь, чтобы дневные планы не менялись.
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `workout_exercise_cross_ref_new` (
                        `workoutId` INTEGER NOT NULL,
                        `exerciseId` INTEGER NOT NULL,
                        `dailyTargetReps` INTEGER NOT NULL,
                        `repsPerSet` INTEGER NOT NULL,
                        PRIMARY KEY(`workoutId`, `exerciseId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `workout_exercise_cross_ref_new`
                        (`workoutId`, `exerciseId`, `dailyTargetReps`, `repsPerSet`)
                    SELECT cr.`workoutId`, cr.`exerciseId`, cr.`dailyTargetReps`,
                           CASE WHEN e.`repsPerSet` > 0 THEN e.`repsPerSet` ELSE 20 END
                    FROM `workout_exercise_cross_ref` cr
                    INNER JOIN `exercises` e ON e.`id` = cr.`exerciseId`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `workout_exercise_cross_ref`")
                db.execSQL(
                    "ALTER TABLE `workout_exercise_cross_ref_new` RENAME TO `workout_exercise_cross_ref`",
                )

                db.execSQL(
                    """
                    CREATE TABLE `exercises_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `exercises_new` (`id`, `name`, `description`)
                    SELECT `id`, `name`, '' FROM `exercises`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `exercises`")
                db.execSQL("ALTER TABLE `exercises_new` RENAME TO `exercises`")
            }
        }

        // v10: у упражнения вместо описания появляются категория, единица и имя иконки;
        // у тренировки — доступное время и настройки напоминаний.
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `exercises_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `iconResName` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `exercises_new` (`id`, `name`, `category`, `unit`, `iconResName`)
                    SELECT `id`, `name`, 'Грудь', 'Повторения', 'fitness_center' FROM `exercises`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `exercises`")
                db.execSQL("ALTER TABLE `exercises_new` RENAME TO `exercises`")

                db.execSQL(
                    "ALTER TABLE `workouts` ADD COLUMN `availableTimeMinutes` " +
                        "INTEGER NOT NULL DEFAULT 60",
                )
                db.execSQL(
                    "ALTER TABLE `workouts` ADD COLUMN `notificationsEnabled` " +
                        "INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE `workouts` ADD COLUMN `remindBeforeMinutes` " +
                        "INTEGER NOT NULL DEFAULT 5",
                )
            }
        }

        // v11: справочник трофеев. Migration создаёт таблицу, callback заполняет её
        // и для новых установок, и для уже обновлённых баз.
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `achievements` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        `isUnlocked` INTEGER NOT NULL,
                        `unlockedDate` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        // v12: удаляем «время на тренировку». Отдых теперь всегда считается от времени
        // бодрствования, поэтому колонка больше не нужна; остальные данные сохраняются.
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `workouts_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `startDate` INTEGER NOT NULL,
                        `periodDays` INTEGER NOT NULL,
                        `sleepStart` TEXT NOT NULL,
                        `sleepEnd` TEXT NOT NULL,
                        `notificationsEnabled` INTEGER NOT NULL,
                        `remindBeforeMinutes` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `workouts_new`
                        (`id`, `name`, `startDate`, `periodDays`, `sleepStart`, `sleepEnd`,
                         `notificationsEnabled`, `remindBeforeMinutes`)
                    SELECT `id`, `name`, `startDate`, `periodDays`, `sleepStart`, `sleepEnd`,
                           `notificationsEnabled`, `remindBeforeMinutes` FROM `workouts`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `workouts`")
                db.execSQL("ALTER TABLE `workouts_new` RENAME TO `workouts`")
            }
        }

        // v13: убираем «глупые» трофеи. Новые записи добавит seed-callback при открытии БД.
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val removed = listOf(
                    "first_workout", "first_set", "first_day", "first_finish", "novice",
                    "organizer", "worker", "athlete", "heavy", "titan", "sleep_setup",
                    "auto_rest", "perfectionist", "editor", "first_gym",
                )
                removed.forEach { id ->
                    db.execSQL("DELETE FROM `achievements` WHERE `id` = ?", arrayOf(id))
                }
            }
        }

        private val ACHIEVEMENT_SEED_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedAchievements(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                seedAchievements(db)
            }
        }

        private fun seedAchievements(db: SupportSQLiteDatabase) {
            val sql = """
                INSERT OR IGNORE INTO `achievements`
                    (`id`, `title`, `description`, `emoji`, `isUnlocked`, `unlockedDate`)
                VALUES (?, ?, ?, ?, 0, NULL)
            """.trimIndent()
            AchievementRepository.allAchievements.forEach { achievement ->
                db.execSQL(
                    sql,
                    arrayOf(achievement.id, achievement.title, achievement.description, achievement.emoji),
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
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                        MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                        MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                    )
                    .addCallback(ACHIEVEMENT_SEED_CALLBACK)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
