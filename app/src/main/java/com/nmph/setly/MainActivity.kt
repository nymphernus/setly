package com.nmph.setly

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import com.nmph.setly.data.auth.AuthManager
import com.nmph.setly.data.local.AppDatabase
import com.nmph.setly.ui.MainScreen
import com.nmph.setly.ui.ProfileScreen
import com.nmph.setly.ui.theme.SetlyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var authManager: AuthManager
    private val signInRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)
        // Ранняя инициализация БД: падение схемы проявится сразу, а не при первом чтении.
        AppDatabase.getDatabase(this)

        setContent {
            SetlyTheme {
                var screen by remember { mutableStateOf(Screen.Main) }
                var signedIn by remember { mutableStateOf(authManager.isSignedIn) }
                var email by remember { mutableStateOf(authManager.currentUser?.email) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Setly") },
                            actions = {
                                IconButton(onClick = { screen = Screen.Profile }) {
                                    Icon(Icons.Default.Person, contentDescription = "Профиль")
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    Box(Modifier.fillMaxSize()) {
                        when (screen) {
                            Screen.Main -> MainScreen(Modifier.padding(innerPadding))
                            Screen.Profile -> ProfileScreen(
                                isSignedIn = signedIn,
                                email = email,
                                onSignIn = { authManager.signInWithGoogle(this@MainActivity, signInRequestCode) },
                                onSync = {
                                    Toast.makeText(this@MainActivity, "Синхронизация скоро", Toast.LENGTH_SHORT).show()
                                },
                                onSignOut = {
                                    authManager.signOut()
                                    signedIn = false
                                    email = null
                                },
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
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
                .onSuccess {
                    // Firebase Auth сохранил сессию; пересоздаём Activity, чтобы обновить состояние входа.
                    recreate()
                }
                .onFailure { e ->
                    Toast.makeText(this@MainActivity, "Ошибка входа: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}

private enum class Screen { Main, Profile }
