package com.nmph.setly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.firestore.FirebaseFirestore
import com.nmph.setly.data.auth.AuthManager
import com.nmph.setly.data.local.AppDatabase
import com.nmph.setly.data.local.WorkoutPlan
import com.nmph.setly.data.settings.AppSettings
import com.nmph.setly.data.sync.SyncManager
import com.nmph.setly.ui.AddEditExerciseScreen
import com.nmph.setly.ui.AddEditWorkoutScreen
import com.nmph.setly.ui.DayDetailScreen
import com.nmph.setly.ui.MainScreen
import com.nmph.setly.ui.SplashScreen
import com.nmph.setly.ui.WorkoutDetailScreen
import com.nmph.setly.ui.theme.SetlyTheme
import com.nmph.setly.utils.AchievementChecker
import com.nmph.setly.utils.AchievementEvents
import com.nmph.setly.ui.viewmodel.AchievementsViewModel
import com.nmph.setly.ui.viewmodel.AchievementsViewModelFactory
import com.nmph.setly.ui.viewmodel.DayDetailViewModel
import com.nmph.setly.ui.viewmodel.DayDetailViewModelFactory
import com.nmph.setly.ui.viewmodel.ExerciseViewModel
import com.nmph.setly.ui.viewmodel.ExerciseViewModelFactory
import com.nmph.setly.ui.viewmodel.HistoryViewModel
import com.nmph.setly.ui.viewmodel.HistoryViewModelFactory
import com.nmph.setly.ui.viewmodel.ProfileViewModel
import com.nmph.setly.ui.viewmodel.ProfileViewModelFactory
import com.nmph.setly.ui.viewmodel.TodayViewModel
import com.nmph.setly.ui.viewmodel.TodayViewModelFactory
import com.nmph.setly.ui.viewmodel.WorkoutDetailViewModel
import com.nmph.setly.ui.viewmodel.WorkoutDetailViewModelFactory
import com.nmph.setly.ui.viewmodel.WorkoutViewModel
import com.nmph.setly.ui.viewmodel.WorkoutViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainActivity : ComponentActivity() {
    private lateinit var authManager: AuthManager
    private lateinit var syncManager: SyncManager
    private lateinit var database: AppDatabase
    private val signInRequestCode = 1001

    // Уведомление/внешняя deep link может прийти до создания NavHost или уже в запущенном приложении.
    private val pendingWorkoutId = mutableStateOf<Int?>(null)

    // Уведомления опциональны: отказ пользователя не ломает работу приложения.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncManager = SyncManager(this, FirebaseFirestore.getInstance())
        authManager = AuthManager(this, syncManager)
        val settings = AppSettings(this)
        // Ранняя инициализация БД: падение схемы проявится сразу, а не при первом чтении.
        database = AppDatabase.getDatabase(this)

        handleDeepLink(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val darkTheme by settings.darkTheme.collectAsState()
            val notificationsEnabled by settings.notificationsEnabled.collectAsState()
            var snackbarMessage by remember { mutableStateOf<String?>(null) }
            val achievementQueue = remember { mutableStateListOf<String>() }

            // Трофеи приходят как события; показываем их по очереди, не теряя при смене экрана.
            LaunchedEffect(Unit) {
                AchievementEvents.messages.collect { message -> achievementQueue.add(message) }
            }
            LaunchedEffect(snackbarMessage, achievementQueue.size) {
                if (snackbarMessage == null && achievementQueue.isNotEmpty()) {
                    snackbarMessage = achievementQueue.removeAt(0)
                }
            }

            SetlyTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()

                LaunchedEffect(pendingWorkoutId.value) {
                    val id = pendingWorkoutId.value ?: return@LaunchedEffect
                    if (navController.currentDestination?.route == "splash") {
                        navController.navigate("home_root") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                    navController.navigate("workout/$id") { launchSingleTop = true }
                    pendingWorkoutId.value = null
                }

                val exerciseVM: ExerciseViewModel = viewModel(
                    factory = ExerciseViewModelFactory(database.exerciseDao(), database.workoutDao()),
                )
                val workoutVM: WorkoutViewModel = viewModel(
                    factory = WorkoutViewModelFactory(
                        database.workoutDao(), database.exerciseDao(), database.progressDao(),
                        syncManager, database.achievementDao(),
                    ),
                )
                val todayVM: TodayViewModel = viewModel(
                    factory = TodayViewModelFactory(
                        database.workoutDao(), database.exerciseDao(), database.progressDao(),
                        syncManager, database.achievementDao(),
                    ),
                )
                val achievementsVM: AchievementsViewModel = viewModel(
                    factory = AchievementsViewModelFactory(database.achievementDao()),
                )
                val historyVM: HistoryViewModel = viewModel(
                    factory = HistoryViewModelFactory(
                        database.workoutDao(), database.progressDao(), syncManager,
                    ),
                )
                val profileVM: ProfileViewModel = viewModel(
                    factory = ProfileViewModelFactory(database.progressDao(), settings),
                )

                val workouts by workoutVM.workouts.collectAsState()
                val exercises by exerciseVM.exercises.collectAsState()
                val stats by profileVM.stats.collectAsState()
                val lastSyncAt by syncManager.lastSyncAt.collectAsState()
                val lastSyncError by syncManager.lastSyncError.collectAsState()
                val isSyncing by syncManager.isSyncing.collectAsState()
                val todayWorkouts by todayVM.todayWorkouts.collectAsState()

                // Напоминание привязано к конкретной активной тренировке и её настройкам.
                val notifiedWorkout = workouts.firstOrNull {
                    it.workout.notificationsEnabled && isActiveToday(it.workout)
                }
                LaunchedEffect(
                    notificationsEnabled,
                    notifiedWorkout?.workout?.id,
                    notifiedWorkout?.workout?.remindBeforeMinutes,
                ) {
                    val workManager = WorkManager.getInstance(applicationContext)
                    val target = notifiedWorkout
                    if (notificationsEnabled && target != null) {
                        val inputData = Data.Builder()
                            .putInt(NotificationWorker.KEY_WORKOUT_ID, target.workout.id)
                            .putInt(
                                NotificationWorker.KEY_REMIND_BEFORE_MINUTES,
                                target.workout.remindBeforeMinutes,
                            )
                            .build()
                        workManager.enqueueUniquePeriodicWork(
                            "setly_workout_reminder",
                            ExistingPeriodicWorkPolicy.UPDATE,
                            PeriodicWorkRequestBuilder<NotificationWorker>(30, TimeUnit.MINUTES)
                                .setInputData(inputData)
                                .build(),
                        )
                    } else {
                        workManager.cancelUniqueWork("setly_workout_reminder")
                    }
                }

                NavHost(navController = navController, startDestination = "splash") {
                    composable("splash") {
                        SplashScreen(
                            onDone = {
                                navController.navigate("home_root") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            },
                        )
                    }
                    composable("home_root") {
                        MainScreen(
                            workouts = workouts,
                            todayWorkouts = todayWorkouts,
                            stats = stats,
                            historyViewModel = historyVM,
                            exerciseViewModel = exerciseVM,
                            achievementsViewModel = achievementsVM,
                            isSignedIn = authManager.isSignedIn,
                            email = authManager.currentUser?.email,
                            lastSyncAt = lastSyncAt,
                            lastSyncError = lastSyncError,
                            isSyncing = isSyncing,
                            darkTheme = darkTheme,
                            notificationsEnabled = notificationsEnabled,
                            snackbarMessage = snackbarMessage,
                            onSnackbarShown = { snackbarMessage = null },
                            onAddWorkout = { navController.navigate("edit_workout/-1") },
                            onEditExercise = { id -> navController.navigate("exercise/$id") },
                            onOpenDay = { date -> navController.navigate("day_detail/$date") },
                            onViewPreviousMonth = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    AchievementChecker.unlockDirect(
                                        database.achievementDao(), "calendar",
                                    )
                                }
                            },
                            onOpenWorkout = { id -> navController.navigate("workout/$id") },
                            onEditWorkout = { id -> navController.navigate("edit_workout/$id") },
                            onDeleteWorkout = { item ->
                                workoutVM.deleteWorkout(item.workout) {
                                    snackbarMessage = "Тренировка удалена"
                                }
                            },
                            onToggleDarkTheme = { settings.setDarkTheme(it) },
                            onToggleNotifications = { settings.setNotificationsEnabled(it) },
                            onSignIn = {
                                syncManager.clearError()
                                authManager.signInWithGoogle(this@MainActivity, signInRequestCode)
                            },
                            onSync = {
                                if (!authManager.isSignedIn) {
                                    snackbarMessage = "Войдите через Google для синхронизации"
                                } else {
                                    workoutVM.syncNow { success ->
                                        snackbarMessage = if (success) {
                                            "Синхронизация завершена"
                                        } else {
                                            syncManager.lastSyncError.value
                                                ?.let { "Ошибка синхронизации: $it" }
                                                ?: "Не удалось синхронизировать данные"
                                        }
                                        if (success) {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                AchievementChecker.unlockDirect(
                                                    database.achievementDao(), "sync",
                                                )
                                                AchievementChecker.unlockDirect(
                                                    database.achievementDao(), "cloud",
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            onSignOut = {
                                authManager.signOut()
                                snackbarMessage = "Вы вышли из аккаунта"
                            },
                        )
                    }
                    composable(
                        route = "workout/{workoutId}",
                        arguments = listOf(navArgument("workoutId") {
                            type = NavType.IntType
                            defaultValue = -1
                        }),
                    ) { entry ->
                        val id = entry.arguments?.getInt("workoutId") ?: -1
                        val selectedWorkout: WorkoutPlan? = workouts.firstOrNull { it.workout.id == id }
                        val detailVM: WorkoutDetailViewModel = viewModel(
                            key = "workout_$id",
                            factory = WorkoutDetailViewModelFactory(
                                id, database.workoutDao(), database.progressDao(),
                            ),
                        )
                        WorkoutDetailScreen(
                            workout = selectedWorkout,
                            todayViewModel = todayVM,
                            detailViewModel = detailVM,
                            onBack = { navController.popBackStack() },
                            onEdit = { navController.navigate("edit_workout/$id") },
                            onComplete = {
                                snackbarMessage = "Тренировка завершена"
                                navController.popBackStack()
                            },
                        )
                    }
                    composable(
                        route = "edit_workout/{workoutId}",
                        arguments = listOf(navArgument("workoutId") {
                            type = NavType.IntType
                            defaultValue = -1
                        }),
                    ) { entry ->
                        val id = entry.arguments?.getInt("workoutId") ?: -1
                        val editingPlan: WorkoutPlan? = if (id == -1) {
                            null
                        } else {
                            workouts.firstOrNull { it.workout.id == id }
                        }
                        val initialSettings = editingPlan?.exercises
                            ?.associate { it.exercise.id to (it.dailyTargetReps to it.repsPerSet) }
                            ?: emptyMap()
                        AddEditWorkoutScreen(
                            workout = editingPlan,
                            availableExercises = exercises,
                            initialSettings = initialSettings,
                            onSave = { name, period, sleepStart, sleepEnd,
                                notifications, remindBefore, settings ->
                                workoutVM.saveWorkout(
                                    id, name, period, sleepStart, sleepEnd,
                                    notifications, remindBefore, settings,
                                ) { message ->
                                    snackbarMessage = message
                                    navController.popBackStack()
                                }
                            },
                            onCreateExercise = { name, category, unit, iconResName, onCreated ->
                                exerciseVM.addExercise(name, category, unit, iconResName, onCreated)
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = "exercise/{exerciseId}",
                        arguments = listOf(navArgument("exerciseId") {
                            type = NavType.IntType
                            defaultValue = -1
                        }),
                    ) { entry ->
                        val id = entry.arguments?.getInt("exerciseId") ?: -1
                        val editing = if (id != -1) exerciseVM.getExerciseById(id) else null
                        AddEditExerciseScreen(
                            exercise = editing,
                            onSave = { name, category, unit, iconResName ->
                                if (editing == null) {
                                    exerciseVM.addExercise(name, category, unit, iconResName)
                                } else {
                                    exerciseVM.updateExercise(
                                        editing.copy(
                                            name = name,
                                            category = category,
                                            unit = unit,
                                            iconResName = iconResName,
                                        ),
                                    )
                                }
                                snackbarMessage = "Упражнение сохранено"
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = "day_detail/{date}",
                        arguments = listOf(navArgument("date") { type = NavType.StringType }),
                    ) { entry ->
                        val date = entry.arguments?.getString("date")
                            ?: LocalDate.now().toString()
                        val dayVM: DayDetailViewModel = viewModel(
                            key = "day_$date",
                            factory = DayDetailViewModelFactory(
                                date, database.workoutDao(), database.progressDao(),
                            ),
                        )
                        DayDetailScreen(
                            date = date,
                            viewModel = dayVM,
                            workouts = workouts,
                            onFillDay = { plan ->
                                dayVM.fillDay(plan) { message -> snackbarMessage = message }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent == null) return
        val fromExtra = intent.getIntExtra(EXTRA_WORKOUT_ID, -1).takeIf { it > 0 }
        val fromUri = intent.data?.let { uri ->
            if (uri.scheme == DEEP_LINK_SCHEME && uri.host == HOST_WORKOUT_DETAIL) {
                uri.lastPathSegment?.toIntOrNull()?.takeIf { it > 0 }
            } else {
                null
            }
        }
        val workoutId = fromExtra ?: fromUri
        if (workoutId != null) {
            pendingWorkoutId.value = workoutId
            CoroutineScope(Dispatchers.IO).launch {
                AchievementChecker.unlockDirect(database.achievementDao(), "notified")
            }
        }
        // Очищаем обработанный intent: поворот экрана не должен открывать тренировку заново.
        intent.removeExtra(EXTRA_WORKOUT_ID)
        intent.data = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != signInRequestCode) return
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { authManager.handleSignInResult(data) }
                .onFailure { e ->
                    Toast.makeText(this@MainActivity, "Ошибка входа: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private companion object {
        fun isActiveToday(workout: com.nmph.setly.data.local.WorkoutEntity): Boolean {
            val today = LocalDate.now()
            val start = Instant.ofEpochMilli(workout.startDate)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            return !today.isBefore(start) &&
                !today.isAfter(start.plusDays(workout.periodDays.toLong() - 1))
        }

        const val EXTRA_WORKOUT_ID = "workoutId"
        const val DEEP_LINK_SCHEME = "setly"
        const val HOST_WORKOUT_DETAIL = "workout_detail"
    }
}
