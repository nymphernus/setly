package com.nmph.setly.data.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.nmph.setly.data.sync.SyncManager
import kotlinx.coroutines.tasks.await

class AuthManager(context: Context, private val syncManager: SyncManager? = null) {
    private val appContext = context.applicationContext
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val googleSignInOptions: GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(appContext.getString(com.nmph.setly.R.string.google_web_client_id))
            .requestEmail()
            .build()

    val currentUser get() = auth.currentUser
    val isSignedIn: Boolean get() = auth.currentUser != null

    // Activity сама вызывает startActivityForResult с этим intent.
    fun signInWithGoogle(activity: Activity, requestCode: Int) {
        val intent = GoogleSignIn.getClient(activity, googleSignInOptions).signInIntent
        activity.startActivityForResult(intent, requestCode)
    }

    // Firebase меняет сессию только после exchange id token; без currentUser Firestore недоступен.
    suspend fun handleSignInResult(data: Intent?) {
        val account = GoogleSignIn.getSignedInAccountFromIntent(data).await()
        val idToken = account.idToken ?: return
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
        auth.currentUser?.let { user ->
            firestore.collection("users").document(user.uid)
                .set(mapOf("email" to user.email, "provider" to "google"))
                .await()
            // Подтягиваем данные с других устройств сразу после входа.
            runCatching { syncManager?.pullFromCloud() }
            runCatching { syncManager?.pullProgressForCurrentMonth() }
        }
    }

    fun signOut() {
        auth.signOut()
        GoogleSignIn.getClient(appContext, googleSignInOptions).signOut()
    }
}
