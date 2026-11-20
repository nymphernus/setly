package com.nmph.setly.data.settings

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Простые пользовательские настройки, хранящиеся локально. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val systemDark = when (
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    ) {
        Configuration.UI_MODE_NIGHT_YES -> true
        else -> false
    }

    private val _darkTheme = MutableStateFlow(prefs.getBoolean(KEY_DARK_THEME, systemDark))
    val darkTheme: StateFlow<Boolean> = _darkTheme

    private val _notificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_NOTIFICATIONS, true),
    )
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    private val _autoRestEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_REST, true),
    )
    val autoRestEnabled: StateFlow<Boolean> = _autoRestEnabled

    fun setDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
        _darkTheme.value = enabled
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
        _notificationsEnabled.value = enabled
    }

    fun setAutoRestEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REST, enabled).apply()
        _autoRestEnabled.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "setly_settings"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_AUTO_REST = "auto_rest_enabled"
    }
}
