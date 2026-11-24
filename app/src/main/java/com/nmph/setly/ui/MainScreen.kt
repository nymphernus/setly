package com.nmph.setly.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nmph.setly.data.local.WorkoutPlan
import com.nmph.setly.ui.viewmodel.ExerciseViewModel
import com.nmph.setly.ui.viewmodel.HistoryViewModel
import com.nmph.setly.ui.viewmodel.ProfileStats
import com.nmph.setly.ui.viewmodel.TodayWorkoutItem
import java.time.LocalDate

/** Оболочка с нижней навигацией: каждая вкладка — полноценный маршрут. */
@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun MainScreen(
    workouts: List<WorkoutPlan>,
    todayWorkouts: List<TodayWorkoutItem> = emptyList(),
    stats: ProfileStats,
    historyViewModel: HistoryViewModel,
    exerciseViewModel: ExerciseViewModel,
    isSignedIn: Boolean,
    email: String?,
    lastSyncAt: Long?,
    lastSyncError: String?,
    isSyncing: Boolean,
    darkTheme: Boolean,
    notificationsEnabled: Boolean,
    snackbarMessage: String? = null,
    onSnackbarShown: () -> Unit = {},
    onAddWorkout: () -> Unit,
    onEditExercise: (Int) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenWorkout: (Int) -> Unit,
    onEditWorkout: (Int) -> Unit,
    onDeleteWorkout: (WorkoutPlan) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val currentRoute by navController.currentBackStackEntryAsState()
    val route = currentRoute?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onSnackbarShown()
        }
    }

    val items = listOf(
        Triple("home", "Тренировки", Icons.Default.Home),
        Triple("exercises", "Упражнения", Icons.AutoMirrored.Filled.List),
        Triple("history", "История", Icons.Default.DateRange),
        Triple("profile", "Профиль", Icons.Default.Person),
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (route == "home") {
                FloatingActionButton(
                    onClick = onAddWorkout,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить тренировку")
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                items.forEach { (itemRoute, label, icon) ->
                    NavigationBarItem(
                        selected = route == itemRoute,
                        onClick = {
                            navController.navigate(itemRoute) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(
                "home",
                enterTransition = { fadeIn(animationSpec = tween(180)) },
                exitTransition = { fadeOut(animationSpec = tween(180)) },
            ) {
                HomeScreen(
                    workouts = workouts,
                    todayWorkouts = todayWorkouts,
                    onOpenWorkout = onOpenWorkout,
                    onEditWorkout = onEditWorkout,
                    onDeleteWorkout = onDeleteWorkout,
                    onCreateWorkout = onAddWorkout,
                )
            }
            composable(
                "exercises",
                enterTransition = { fadeIn(animationSpec = tween(180)) },
                exitTransition = { fadeOut(animationSpec = tween(180)) },
            ) {
                ExerciseScreen(
                    viewModel = exerciseViewModel,
                    onEdit = onEditExercise,
                )
            }
            composable(
                "history",
                enterTransition = { fadeIn(animationSpec = tween(180)) },
                exitTransition = { fadeOut(animationSpec = tween(180)) },
            ) {
                HistoryScreen(viewModel = historyViewModel, onOpenDay = onOpenDay)
            }
            composable(
                "profile",
                enterTransition = { fadeIn(animationSpec = tween(180)) },
                exitTransition = { fadeOut(animationSpec = tween(180)) },
            ) {
                ProfileScreen(
                    isSignedIn = isSignedIn,
                    email = email,
                    stats = stats,
                    lastSyncAt = lastSyncAt,
                    lastSyncError = lastSyncError,
                    isSyncing = isSyncing,
                    darkTheme = darkTheme,
                    notificationsEnabled = notificationsEnabled,
                    onToggleDarkTheme = onToggleDarkTheme,
                    onToggleNotifications = onToggleNotifications,
                    onSignIn = onSignIn,
                    onSync = onSync,
                    onSignOut = onSignOut,
                )
            }
        }
    }
}
