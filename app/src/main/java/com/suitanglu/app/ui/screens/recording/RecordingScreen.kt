package com.suitanglu.app.ui.screens.recording

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.suitanglu.app.data.CameraMode
import com.suitanglu.app.service.RecordingService
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    hasCameraPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onFinished: (Long) -> Unit,
    onOpenInAppCamera: () -> Unit,
) {
    val vm: RecordingViewModel = viewModel()
    val context = LocalContext.current
    val state by vm.recordingState.collectAsState()
    val pending by vm.pendingImages.collectAsState()
    val markerCount by vm.markerCount.collectAsState()

    var showMarkerSheet by remember { mutableStateOf(false) }
    var markerText by remember { mutableStateOf("") }
    var showStopConfirm by remember { mutableStateOf(false) }
    var croppingSource by remember { mutableStateOf<Uri?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(UCropContract()) { uri ->
        uri?.let { vm.addCroppedImage(it) }
    }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val src = croppingSource
        if (ok && src != null) {
            cropLauncher.launch(src to newCropUri(context))
        }
        croppingSource = null
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            // 相册图复制到缓存再裁剪
            val dest = File(context.cacheDir, "photos/PICK_${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            cropLauncher.launch(Uri.fromFile(dest) to newCropUri(context))
        }
    }

    // 应用内相机返回的图片 → 裁剪
    LaunchedEffect(Unit) {
        PendingMarkerStore.capturedPath?.let { path ->
            PendingMarkerStore.capturedPath = null
            cropLauncher.launch(Uri.fromFile(File(path)) to newCropUri(context))
        }
    }

    val s = state
    if (s == null) {
        // 首次进入：启动前台录音服务；等待服务就绪时显示启动中
        LaunchedEffect(Unit) {
            RecordingService.start(context)
        }
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("正在启动录音…", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("若长时间无响应，请检查录音权限与其他应用是否占用麦克风。", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onFinished(0) }) { Text("返回") }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(s.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(
            if (s.interrupted) "已被打断（${s.interruptReason ?: "未知原因"}），音频焦点恢复后自动继续"
            else if (s.paused) "已暂停"
            else "正在录音",
            style = MaterialTheme.typography.bodyMedium,
            color = if (s.interrupted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(240.dp)
                .background(
                    if (s.interrupted) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    CircleShape,
                ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatTimer(s.elapsedMs),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    s.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            vm.aiStatusSummary(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { vm.pauseOrResume() },
                modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Icon(
                    if (s.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (s.paused) "继续" else "暂停",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            IconButton(
                onClick = { showStopConfirm = true },
                modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            ) {
                Icon(
                    Icons.Filled.Stop, contentDescription = "停止",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                if (!hasCameraPermission && vm.cameraMode != CameraMode.SYSTEM) {
                    onRequestPermissions()
                    return@Button
                }
                showMarkerSheet = true
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("拍照并标记（已 $markerCount 个）")
        }
    }

    // 停止确认
    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("停止录音") },
            text = { Text("停止后将按标记顺序自动发送 AI 讲解（需要网络）。确定停止吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    val id = s.recordingId
                    vm.stop { }
                    onFinished(id)
                }) { Text("停止") }
            },
            dismissButton = { TextButton(onClick = { showStopConfirm = false }) { Text("继续录音") } },
        )
    }

    // 标记编辑底部弹层
    if (showMarkerSheet) {
        ModalBottomSheet(onDismissRequest = { showMarkerSheet = false }) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (vm.cameraMode == CameraMode.IN_APP) {
                                showMarkerSheet = false
                                onOpenInAppCamera()
                            } else {
                                croppingSource = newPhotoUri(context)
                                takePictureLauncher.launch(croppingSource!!)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("拍照")
                    }
                    FilledTonalButton(
                        onClick = {
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("从相册选")
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (pending.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.height(if (pending.size > 3) 240.dp else 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(pending) { file ->
                            Box {
                                AsyncImage(
                                    model = file, contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().height(80.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                )
                                IconButton(
                                    onClick = { vm.removePending(file) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "移除", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = markerText,
                    onValueChange = { markerText = it },
                    label = { Text("文字备注（可选，不会发送给 AI）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        vm.saveMarker(markerText) { ok ->
                            if (ok) {
                                markerText = ""
                                showMarkerSheet = false
                            }
                        }
                    },
                    enabled = pending.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (pending.isEmpty()) "请先拍照或选择图片" else "保存标记")
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "提示：若系统相机导致录音中断，可在设置中切换为应用内相机。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

private fun formatTimer(ms: Long): String {
    val total = ms / 1000
    return String.format("%02d:%02d:%02d", total / 3600, total % 3600 / 60, total % 60)
}
