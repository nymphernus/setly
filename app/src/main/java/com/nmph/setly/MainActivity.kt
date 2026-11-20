package com.nmph.setly

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nmph.setly.data.auth.AuthManager
import com.nmph.setly.data.local.AppDatabase
import com.nmph.setly.ui.AddEditExerciseScreen
import com.nmph.setly.ui.MainScreen
import com.nmph.setly.ui.ProfileScreen
import com.nmph.setly.ui.theme.SetlyTheme
import com.nmph.setly.ui.viewmodel.ExerciseViewModel
import com.nmph.setly.ui.viewmodel.ExerciseViewModelFactory
import com.nmph.setly.ui.viewmodel.TodayViewModel
import com.nmph.setly.ui.viewmodel.TodayViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var authManager: AuthManager
    private val signInRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)
        // Ранняя инициализация БД: падение схемы проявится сразу, а не при первом чтении.
        val database = AppDatabase.getDatabase(this)

        setContent {
            SetlyTheme {
                val navController = rememberNavController()
                val factory = ExerciseViewModelFactory(database.exerciseDao())
                val viewModel: ExerciseViewModel = viewModel(factory = factory)
                val todayViewModel: TodayViewModel = viewModel(
                    factory = TodayViewModelFactory(database.exerciseDao(), database.progressDao()),
                )
                val exercises by viewModel.exercises.collectAsState()

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            exercises = exercises,
                            todayViewModel = todayViewModel,
                            onAdd = { navController.navigate("add_edit/-1") },
                            onEdit = { id -> navController.navigate("add_edit/$id") },
                            onDelete = { viewModel.deleteExercise(it) },
                            onProfile = { navController.navigate("profile") },
                        )
                    }
                    composable(
                        route = "add_edit/{exerciseId}",
                        arguments = listOf(navArgument("exerciseId") {
                            type = NavType.IntType
                            defaultValue = -1
                        }),
                    ) { backStackEntry ->
                        val id = backStackEntry.arguments?.getInt("exerciseId") ?: -1
                        val editing = if (id != -1) viewModel.getExerciseById(id) else null
                        AddEditExerciseScreen(
                            exercise = editing,
                            onSave = { name, reps, period, sleep ->
                                if (editing == null) {
                                    viewModel.addExercise(name, reps, period, sleep)
                                } else {
                                    viewModel.updateExercise(
                                        editing.copy(
                                            name = name,
                                            targetReps = reps,
                                            periodDays = period,
                                            sleepHours = sleep,
                                        ),
                                    )
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
                            onSignIn = { authManager.signInWithGoogle(this@MainActivity, signInRequestCode) },
                            onSync = {
                                Toast.makeText(this@MainActivity, "Синхронизация скоро", Toast.LENGTH_SHORT).show()
                            },
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
