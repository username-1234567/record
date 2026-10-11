package com.suitanglu.app.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suitanglu.app.AppGraph
import com.suitanglu.app.data.Formatters
import com.suitanglu.app.data.db.Recording
import com.suitanglu.app.service.RecordingBus
import com.suitanglu.app.service.RecordingService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    hasNotificationPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onStartRecording: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecording: (Long) -> Unit,
    onOpenRecycle: () -> Unit,
) {
    val vm: HomeViewModel = viewModel()
    val context = LocalContext.current
    val container = AppGraph.get(context)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val sections by vm.sections.collectAsState()
    val tipShown by vm.firstDeleteTipShown.collectAsState()
    // 用派生布尔值，避免录音计时器每 500ms 触发整页重组
    val isRecording by remember { derivedStateOf { RecordingBus.state.value != null } }

    var pendingDelete by remember { mutableStateOf<Recording?>(null) }
    var pendingAudioOnly by remember { mutableStateOf<Recording?>(null) }
    var renameTarget by remember { mutableStateOf<Recording?>(null) }
    var renameText by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<Recording?>(null) }
    var showNoPermissionHint by remember { mutableStateOf(false) }

    // 授权完成后自动清除提示
    LaunchedEffect(hasNotificationPermission) {
        if (hasNotificationPermission) showNoPermissionHint = false
    }

    var exportTargetId by remember { mutableStateOf(0L) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val id = exportTargetId
        if (uri != null && id > 0) {
            scope.launch {
                val result = container.backupManager.exportSingle(id, uri) { }
                snackbar.showSnackbar(
                    if (result.isSuccess) "已导出单条录音" else "导出失败：${result.exceptionOrNull()?.message}",
                )
            }
        }
        exportTargetId = 0
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("随堂录", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "课堂录音 · 拍照标记 · AI 讲解",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenRecycle) {
                        Icon(Icons.Filled.Delete, contentDescription = "回收站")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (isRecording) {
                        // 正在录音：回录音页
                        onStartRecording()
                    } else {
                        if (hasNotificationPermission || android.os.Build.VERSION.SDK_INT < 33) {
                            onStartRecording()
                        } else {
                            showNoPermissionHint = true
                            onRequestPermissions()
                        }
                    }
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (isRecording) "返回录音中" else "开始录音") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (showNoPermissionHint) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        "需要通知权限以显示录音常驻通知，请允许后在通知栏查看录音状态。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            if (sections.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Audiotrack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("还没有录音", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "点击「开始录音」，上课时拍照标记，\n录音结束 AI 自动讲解",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                    for (section in sections) {
                        item(key = "header_${section.label}") {
                            Text(
                                section.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
                            )
                        }
                        items(section.items, key = { it.recording.id }) { item ->
                            RecordingCard(
                                item = item,
                                onClick = { onOpenRecording(item.recording.id) },
                                onLongPress = { menuFor = item.recording },
                                menuVisible = menuFor?.id == item.recording.id,
                                onDismissMenu = { menuFor = null },
                                onRename = {
                                    renameTarget = item.recording
                                    renameText = item.recording.title
                                    menuFor = null
                                },
                                onDelete = {
                                    pendingDelete = item.recording
                                    menuFor = null
                                },
                                onDeleteAudioOnly = {
                                    pendingAudioOnly = item.recording
                                    menuFor = null
                                },
                                onExport = {
                                    exportTargetId = item.recording.id
                                    exportLauncher.launch("recording_${item.recording.id}.zip")
                                    menuFor = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // 第一次删除提醒
    pendingDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除录音") },
            text = { Text("「${rec.title}」将移入回收站，可前往回收站恢复或彻底删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteToRecycle(rec.id)
                    if (!tipShown) vm.markDeleteTipShown()
                    pendingDelete = null
                }) { Text("移入回收站") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    // 仅删除录音确认
    pendingAudioOnly?.let { rec ->
        AlertDialog(
            onDismissRequest = { pendingAudioOnly = null },
            title = { Text("仅删除录音文件") },
            text = { Text("只删除「${rec.title}」的音频文件，标记、图片和 AI 回复将保留。此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAudioOnly(rec.id)
                    pendingAudioOnly = null
                }) { Text("仅删除音频") }
            },
            dismissButton = { TextButton(onClick = { pendingAudioOnly = null }) { Text("取消") } },
        )
    }

    // 重命名
    renameTarget?.let { rec ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名录音") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("展示名称") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(rec.id, renameText) { ok ->
                        scope.launch {
                            snackbar.showSnackbar(if (ok) "已重命名" else "重命名失败")
                        }
                    }
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingCard(
    item: HomeItem,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    menuVisible: Boolean,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDeleteAudioOnly: () -> Unit,
    onExport: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // 左侧图标
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        if (item.recording.status == Recording.STATUS_AUDIO_DELETED) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (item.recording.status == Recording.STATUS_AUDIO_DELETED) {
                        Icons.Filled.Warning
                    } else {
                        Icons.Outlined.Audiotrack
                    },
                    contentDescription = null,
                    tint = if (item.recording.status == Recording.STATUS_AUDIO_DELETED) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.recording.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                if (item.markerFailed > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${item.markerFailed} 个标记未发送，可重发",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Formatters.duration(item.recording.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${item.markerTotal} 标记",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                DropdownMenu(expanded = menuVisible, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                        onClick = onRename,
                    )
                    DropdownMenuItem(
                        text = { Text("删除到回收站") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = onDelete,
                    )
                    DropdownMenuItem(text = { Text("仅删除录音文件") }, onClick = onDeleteAudioOnly)
                    DropdownMenuItem(
                        text = { Text("导出单条录音") },
                        leadingIcon = { Icon(Icons.Filled.IosShare, null) },
                        onClick = onExport,
                    )
                }
            }
        }
    }
}
