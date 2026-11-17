package com.fabxdi.dibadge.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** The Bluetooth permission is asked once, together with the microphone, so a headset can be used. */
object CallPermissions {
    private const val PREFS = "call_permissions"

    /** [BLUETOOTH_CONNECT] the first time it is needed on Android 12 and newer, otherwise nothing. */
    fun bluetoothOnce(context: Context): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("bluetooth_asked", false)) return emptyList()
        prefs.edit().putBoolean("bluetooth_asked", true).apply()
        return listOf(Manifest.permission.BLUETOOTH_CONNECT)
    }
}