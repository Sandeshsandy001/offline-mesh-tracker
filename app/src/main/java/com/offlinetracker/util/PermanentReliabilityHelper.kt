package com.offlinetracker.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermanentReliabilityHelper {

    /**
     * Checks if this app is exempt from system battery optimizations (Doze & Deep Sleep).
     * When exempt, Android will NEVER sleep or suspend SMS broadcast processing, even after months of inactivity.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens system dialog to whitelist the app from battery optimizations.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimization(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Checks if Background Location ("Allow all the time") is granted.
     * Required on Android 10+ so GPS can be fetched while the screen is off or app is closed.
     */
    fun hasBackgroundLocation(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Opens system App Info page where the user can disable "Pause app activity if unused"
     * (App Hibernation / Permission auto-revoke) and configure permissions.
     */
    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /**
     * Opens the dedicated Unused Apps / Auto-Revoke settings page if supported by Android 11+.
     */
    fun openUnusedAppRestrictionsSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        openAppDetailsSettings(context)
    }
}

