package com.nmph.setly.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Простые пользовательские настройки, хранящиеся локально. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // Новая фирменная палитра по умолчанию светлая; тёмная включается только вручную.
    private val _darkTheme = MutableStateFlow(prefs.getBoolean(KEY_DARK_THEME, false))
    val darkTheme: StateFlow<Boolean> = _darkTheme

    private val _notificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_NOTIFICATIONS, true),
    )
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    fun setDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
        _darkTheme.value = enabled
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
        _notificationsEnabled.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "setly_settings"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
    }
}
