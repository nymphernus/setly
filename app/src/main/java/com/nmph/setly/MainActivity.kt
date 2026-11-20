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
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.firestore.FirebaseFirestore
import com.nmph.setly.data.auth.AuthManager
import com.nmph.setly.data.local.AppDatabase
import com.nmph.setly.data.sync.SyncManager
import com.nmph.setly.ui.AddEditExerciseScreen
import com.nmph.setly.ui.AddEditWorkoutScreen
import com.nmph.setly.ui.MainScreen
import com.nmph.setly.ui.ProfileScreen
import com.nmph.setly.ui.SplashScreen
import com.nmph.setly.ui.theme.SetlyTheme
import com.nmph.setly.ui.viewmodel.ExerciseViewModel
import com.nmph.setly.ui.viewmodel.ExerciseViewModelFactory
import com.nmph.setly.ui.viewmodel.HistoryViewModel
import com.nmph.setly.ui.viewmodel.HistoryViewModelFactory
import com.nmph.setly.ui.viewmodel.TodayViewModel
import com.nmph.setly.ui.viewmodel.TodayViewModelFactory
import com.nmph.setly.ui.viewmodel.WorkoutViewModel
import com.nmph.setly.ui.viewmodel.WorkoutViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

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
        // Ранняя инициализация БД: падение схемы проявится сразу, а не при первом чтении.
        val database = AppDatabase.getDatabase(this)

        // Периодическое напоминание; KEEP не создаёт дубликаты при перезапуске приложения.
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "setly_workout_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NotificationWorker>(30, TimeUnit.MINUTES).build(),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            SetlyTheme {
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
                val workouts by workoutVM.workouts.collectAsState()
                val exercises by exerciseVM.exercises.collectAsState()
                val lastSyncAt by syncManager.lastSyncAt.collectAsState()
                val isSyncing by syncManager.isSyncing.collectAsState()

                NavHost(navController = navController, startDestination = "splash") {
                    composable("splash") {
                        SplashScreen(
                            onDone = {
                                navController.navigate("main") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            },
                        )
                    }
                    composable("main") {
                        MainScreen(
                            workouts = workouts,
                            todayViewModel = todayVM,
                            historyViewModel = historyVM,
                            onAddWorkout = { navController.navigate("workout/-1") },
                            onEditWorkout = { id -> navController.navigate("workout/$id") },
                            onDeleteWorkout = { workoutVM.deleteWorkout(it.workout) },
                            onProfile = { navController.navigate("profile") },
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
                        var editingWorkout by remember { mutableStateOf<com.nmph.setly.data.local.WorkoutWithExercises?>(null) }
                        var initialSelected by remember { mutableStateOf(emptySet<Int>()) }
                        androidx.compose.runtime.LaunchedEffect(id) {
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
                            onSave = { name, period, sleepStart, sleepEnd, restMinutes, selectedIds ->
                                workoutVM.saveWorkout(id, name, period, sleepStart, sleepEnd, restMinutes, selectedIds)
                                navController.popBackStack()
                            },
                            onCreateExercise = { name, reps -> workoutVM.createExercise(name, reps) },
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
                            onSave = { name, reps ->
                                if (editing == null) {
                                    exerciseVM.addExercise(name, reps)
                                } else {
                                    exerciseVM.updateExercise(editing.copy(name = name, targetReps = reps))
                                }
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("profile") {
                        ProfileScreen(
                            isSignedIn = authManager.isSignedIn,
                            email = authManager.currentUser?.email,
                            lastSyncAt = lastSyncAt,
                            isSyncing = isSyncing,
                            onSignIn = { authManager.signInWithGoogle(this@MainActivity, signInRequestCode) },
                            onSync = { workoutVM.syncNow() },
                            onSignOut = { authManager.signOut() },
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
