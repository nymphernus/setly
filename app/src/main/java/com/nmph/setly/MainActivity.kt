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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.firestore.FirebaseFirestore
import com.nmph.setly.data.auth.AuthManager
import com.nmph.setly.data.local.AppDatabase
import com.nmph.setly.data.local.WorkoutWithExercises
import com.nmph.setly.data.settings.AppSettings
import com.nmph.setly.data.sync.SyncManager
import com.nmph.setly.ui.AddEditExerciseScreen
import com.nmph.setly.ui.AddEditWorkoutScreen
import com.nmph.setly.ui.MainScreen
import com.nmph.setly.ui.SplashScreen
import com.nmph.setly.ui.WorkoutDetailScreen
import com.nmph.setly.ui.theme.SetlyTheme
import com.nmph.setly.ui.viewmodel.ExerciseViewModel
import com.nmph.setly.ui.viewmodel.ExerciseViewModelFactory
import com.nmph.setly.ui.viewmodel.HistoryViewModel
import com.nmph.setly.ui.viewmodel.HistoryViewModelFactory
import com.nmph.setly.ui.viewmodel.ProfileViewModel
import com.nmph.setly.ui.viewmodel.ProfileViewModelFactory
import com.nmph.setly.ui.viewmodel.TodayViewModel
import com.nmph.setly.ui.viewmodel.TodayViewModelFactory
import com.nmph.setly.ui.viewmodel.WorkoutViewModel
import com.nmph.setly.ui.viewmodel.WorkoutViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainActivity : ComponentActivity() {
    private lateinit var authManager: AuthManager
    private lateinit var syncManager: SyncManager
    private val signInRequestCode = 1001

    // Уведомления опциональны: отказ пользователя не ломает работу приложения.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncManager = SyncManager(this, FirebaseFirestore.getInstance())
        authManager = AuthManager(this, syncManager)
        val settings = AppSettings(this)
        // Ранняя инициализация БД: падение схемы проявится сразу, а не при первом чтении.
        val database = AppDatabase.getDatabase(this)

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

            // Напоминание включается/выключается из настроек профиля.
            LaunchedEffect(notificationsEnabled) {
                val workManager = WorkManager.getInstance(applicationContext)
                if (notificationsEnabled) {
                    workManager.enqueueUniquePeriodicWork(
                        "setly_workout_reminder",
                        ExistingPeriodicWorkPolicy.KEEP,
                        PeriodicWorkRequestBuilder<NotificationWorker>(30, TimeUnit.MINUTES).build(),
                    )
                } else {
                    workManager.cancelUniqueWork("setly_workout_reminder")
                }
            }

            SetlyTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                val exerciseVM: ExerciseViewModel = viewModel(
                    factory = ExerciseViewModelFactory(database.exerciseDao()),
                )
                val workoutVM: WorkoutViewModel = viewModel(
                    factory = WorkoutViewModelFactory(
                        database.workoutDao(), database.exerciseDao(), syncManager,
                    ),
                )
                val todayVM: TodayViewModel = viewModel(
                    factory = TodayViewModelFactory(
                        database.workoutDao(), database.exerciseDao(), database.progressDao(), syncManager,
                    ),
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
                val isSyncing by syncManager.isSyncing.collectAsState()
                val todayWorkouts by todayVM.todayWorkouts.collectAsState()

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
                            isSignedIn = authManager.isSignedIn,
                            email = authManager.currentUser?.email,
                            lastSyncAt = lastSyncAt,
                            isSyncing = isSyncing,
                            darkTheme = darkTheme,
                            notificationsEnabled = notificationsEnabled,
                            snackbarMessage = snackbarMessage,
                            onSnackbarShown = { snackbarMessage = null },
                            onAddWorkout = { navController.navigate("edit_workout/-1") },
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
                                            "Не удалось синхронизировать данные"
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
                        var selectedWorkout by remember { mutableStateOf<WorkoutWithExercises?>(null) }
                        LaunchedEffect(id) {
                            workoutVM.getWorkoutById(id) { found -> selectedWorkout = found }
                        }
                        WorkoutDetailScreen(
                            workout = selectedWorkout,
                            todayViewModel = todayVM,
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
                        var editingWorkout by remember { mutableStateOf<WorkoutWithExercises?>(null) }
                        var initialSelected by remember { mutableStateOf(emptySet<Int>()) }
                        LaunchedEffect(id) {
                            if (id != -1) {
                                workoutVM.getWorkoutById(id) { found ->
                                    editingWorkout = found
                                    initialSelected = found?.exercises?.map { it.id }?.toSet() ?: emptySet()
                                }
                            }
                        }
                        AddEditWorkoutScreen(
                            workout = editingWorkout?.workout,
                            availableExercises = exercises,
                            initialSelectedIds = initialSelected,
                            onSave = { name, period, sleepStart, sleepEnd, restMinutes, autoRest, selectedIds ->
                                workoutVM.saveWorkout(
                                    id, name, period, sleepStart, sleepEnd,
                                    restMinutes, autoRest, selectedIds,
                                ) { message ->
                                    snackbarMessage = message
                                    navController.popBackStack()
                                }
                            },
                            onCreateExercise = { name, reps, perSet ->
                                workoutVM.createExercise(name, reps, perSet)
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
                            onSave = { name, reps, perSet ->
                                if (editing == null) {
                                    exerciseVM.addExercise(name, reps, perSet)
                                } else {
                                    exerciseVM.updateExercise(
                                        editing.copy(name = name, targetReps = reps, repsPerSet = perSet),
                                    )
                                }
                                snackbarMessage = "Упражнение сохранено"
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
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
}
