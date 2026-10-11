package com.suitanglu.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.suitanglu.app.AppGraph
import com.suitanglu.app.ui.nav.RecordNavHost
import com.suitanglu.app.ui.theme.RecordTheme

class MainActivity : ComponentActivity() {

    private var grantedPermissions by mutableStateOf(setOf<String>())

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            grantedPermissions = result.filterValues { it }.keys
        }

    fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppGraph.init(application)
        grantedPermissions = setOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            .filterTo(mutableSetOf()) { hasPermission(it) }

        setContent {
            RecordTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RecordNavHost(
                        hasRecordPermission = hasPermission(Manifest.permission.RECORD_AUDIO),
                        hasCameraPermission = hasPermission(Manifest.permission.CAMERA),
                        hasNotificationPermission = if (Build.VERSION.SDK_INT >= 33) {
                            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                        } else true,
                        onRequestPermissions = {
                            val list = mutableListOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.CAMERA,
                            )
                            if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
                            permissionLauncher.launch(list.toTypedArray())
                        },
                    )
                }
            }
        }
    }
}
