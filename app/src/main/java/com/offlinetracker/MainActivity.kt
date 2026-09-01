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

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
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
                    var isSetupDone by remember { mutableStateOf<Boolean?>(null) }

                    LaunchedEffect(Unit) {
                        val done = withContext(Dispatchers.IO) {
                            db.appConfigDao().get("INITIAL_SETUP_DONE") == "true"
                        }
                        isSetupDone = done
                    }

                    when (isSetupDone) {
                        null -> {
                            // Loading state
                        }
                        false -> {
                            val onboardingViewModel = remember { OnboardingViewModel(db) }
                            OnboardingScreen(
                                viewModel = onboardingViewModel,
                                onSetupComplete = { isSetupDone = true }
                            )
                        }
                        true -> {
                            val dashboardViewModel = remember { DashboardViewModel(db) }
                            FamilyDashboardScreen(
                                viewModel = dashboardViewModel,
                                onNavigateToSettings = { isSetupDone = false }
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
