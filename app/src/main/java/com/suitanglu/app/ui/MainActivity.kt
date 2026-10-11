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
        grantedPermissions = buildSet {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filterTo(mutableSetOf()) { hasPermission(it) }

        setContent {
            RecordTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RecordNavHost(
                        // 读 grantedPermissions 状态而不是一次性 checkSelfPermission，
                        // 否则授权回调后组合不会刷新，授权完仍无法开始录音/拍照
                        hasRecordPermission = grantedPermissions.contains(Manifest.permission.RECORD_AUDIO),
                        hasCameraPermission = grantedPermissions.contains(Manifest.permission.CAMERA),
                        hasNotificationPermission = Build.VERSION.SDK_INT < 33 ||
                            grantedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS),
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
