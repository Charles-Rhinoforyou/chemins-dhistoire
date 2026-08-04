package com.cheminsdhistoire.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.cheminsdhistoire.app.playback.PlaybackController
import com.cheminsdhistoire.app.service.PlaybackService
import com.cheminsdhistoire.app.ui.CheminsApp

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val locationOk = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationOk) startEngine()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlaybackController.init(this)

        setContent {
            CheminsApp(
                onRequestStart = { ensurePermissionsAndStart() }
            )
        }

        if (hasLocationPermission()) startEngine() else ensurePermissionsAndStart()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensurePermissionsAndStart() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (hasLocationPermission()) startEngine() else permissionLauncher.launch(perms.toTypedArray())
    }

    private fun startEngine() {
        PlaybackService.start(this)
        PlaybackController.start()
    }
}
