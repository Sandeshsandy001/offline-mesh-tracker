package com.offlinetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinetracker.data.local.OfflineMeshDatabase
import com.offlinetracker.ui.dashboard.DashboardViewModel
import com.offlinetracker.ui.dashboard.FamilyDashboardScreen
import com.offlinetracker.ui.onboarding.OnboardingScreen
import com.offlinetracker.ui.onboarding.OnboardingViewModel
import com.offlinetracker.ui.theme.OfflineTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppScreen {
    Loading,
    Onboarding,
    Dashboard,
    Settings
}

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Handle permission results if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()

        val db = OfflineMeshDatabase.get(applicationContext)

        setContent {
            OfflineTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf(AppScreen.Loading) }

                    LaunchedEffect(Unit) {
                        val done = withContext(Dispatchers.IO) {
                            db.appConfigDao().get("INITIAL_SETUP_DONE") == "true"
                        }
                        currentScreen = if (done) AppScreen.Dashboard else AppScreen.Onboarding
                    }

                    when (currentScreen) {
                        AppScreen.Loading -> {
                            // Loading state
                        }
                        AppScreen.Onboarding -> {
                            val onboardingViewModel = remember { OnboardingViewModel(db) }
                            OnboardingScreen(
                                viewModel = onboardingViewModel,
                                isSettingsMode = false,
                                onSetupComplete = { currentScreen = AppScreen.Dashboard }
                            )
                        }
                        AppScreen.Dashboard -> {
                            val dashboardViewModel = remember { DashboardViewModel(db) }
                            FamilyDashboardScreen(
                                viewModel = dashboardViewModel,
                                onNavigateToSettings = { currentScreen = AppScreen.Settings }
                            )
                        }
                        AppScreen.Settings -> {
                            val settingsViewModel = remember { OnboardingViewModel(db) }
                            OnboardingScreen(
                                viewModel = settingsViewModel,
                                isSettingsMode = true,
                                onSetupComplete = { currentScreen = AppScreen.Dashboard },
                                onCancel = { currentScreen = AppScreen.Dashboard }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted.toTypedArray())
        }
    }
}
