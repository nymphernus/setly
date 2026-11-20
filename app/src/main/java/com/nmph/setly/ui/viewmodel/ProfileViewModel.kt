package com.nmph.setly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nmph.setly.data.local.ProgressDao
import com.nmph.setly.data.settings.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class ProfileStats(
    val completedWorkouts: Int = 0,
    val streakDays: Int = 0,
    val totalSets: Int = 0,
)

class ProfileViewModel(
    progressDao: ProgressDao,
    private val settings: AppSettings,
) : ViewModel() {
    val stats: StateFlow<ProfileStats> = progressDao.observeAllProgress()
        .map { progress ->
            val completedDates = progress
                .filter { it.isCompleted }
                .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
                .distinct()
                .sorted()

            var cursor = LocalDate.now()
            if (completedDates.none { it == cursor }) cursor = cursor.minusDays(1)
            var streak = 0
            while (completedDates.contains(cursor)) {
                streak++
                cursor = cursor.minusDays(1)
            }

            ProfileStats(
                completedWorkouts = completedDates.size,
                streakDays = streak,
                totalSets = progress.sumOf { it.completedSets.size },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileStats())

    val darkTheme: StateFlow<Boolean> = settings.darkTheme
    val notificationsEnabled: StateFlow<Boolean> = settings.notificationsEnabled

    fun setDarkTheme(enabled: Boolean) = settings.setDarkTheme(enabled)

    fun setNotificationsEnabled(enabled: Boolean) = settings.setNotificationsEnabled(enabled)
}

class ProfileViewModelFactory(
    private val progressDao: ProgressDao,
    private val settings: AppSettings,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            return ProfileViewModel(progressDao, settings) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
