package com.farm_tech.farmhub.features

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Usage:
 * Call PermissionsHandler() at the top of your FarmHelp composable.
 * Pass onGranted: () -> Unit to launch your camera/gallery intent.
 * Optional: Pass onDenied: () -> Unit to show a message, etc.
 * Set requestNow = true when you want to trigger the request (e.g. button click).
 */
@Composable
fun PermissionsHandler(
    onGranted: () -> Unit,
    onDenied: (() -> Unit)? = null,
    requestNow: Boolean = false
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var permissionRequested by remember { mutableStateOf(false) }

    // All permissions required
    val permissionsToRequest = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
    // WRITE_EXTERNAL_STORAGE for Android < Q (API 29)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    // Check if all permissions are granted
    val allGranted = permissionsToRequest.all { perm ->
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(requestNow, permissionRequested, allGranted) {
        if (requestNow && !permissionRequested && activity != null && !allGranted) {
            permissionRequested = true
            ActivityCompat.requestPermissions(activity, permissionsToRequest.toTypedArray(), 100)
        }
    }

    if (allGranted) {
        onGranted()
    } else if (permissionRequested && onDenied != null) {
        onDenied()
    }
}
