package com.suitanglu.app.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suitanglu.app.data.CameraMode
import com.suitanglu.app.data.Formatters
import com.suitanglu.app.update.UpdateConfig
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSchedule: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val ai by vm.aiConfig.collectAsState()
    val general by vm.generalConfig.collectAsState()
    val aiTestResult by vm.aiTestResult.collectAsState()
    val aiTesting by vm.aiTesting.collectAsState()
    val backupProgress by vm.backupProgress.collectAsState()
    val backupResult by vm.backupResult.collectAsState()
    val storage by vm.storageUsage.collectAsState()
    val updateState by vm.updateState.collectAsState()

    // 本地编辑态
    var apiKey by remember(ai.apiKey) { mutableStateOf(ai.apiKey) }
    var baseUrl by remember(ai.baseUrl) { mutableStateOf(ai.baseUrl) }
    var model by remember(ai.model) { mutableStateOf(ai.model) }
    var stream by remember(ai.stream) { mutableStateOf(ai.stream) }
    var timeout by remember(ai.timeoutSeconds) { mutableStateOf(ai.timeoutSeconds.toString()) }
    var maxTokens by remember(ai.maxTokens) { mutableStateOf(ai.maxTokens.toString()) }
    var prompt by remember(ai.prompt) { mutableStateOf(ai.prompt) }

    var sizeLimit by remember(general.sizeLimitMb) { mutableStateOf(general.sizeLimitMb.toString()) }
    var compress by remember(general.compressImages) { mutableStateOf(general.compressImages) }
    var compressQuality by remember(general.compressQuality) { mutableStateOf(general.compressQuality.toFloat()) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { vm.exportTo(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.importFrom(it) } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ===== 课程表 =====
            SectionCard("课程表", Icons.Filled.CalendarMonth) {
                Text(
                    "按星期几配置五个固定时间段的课程名称，录音开始时自动命中命名。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = onOpenSchedule) { Text("编辑课程表") }
            }

            // ===== AI 配置 =====
            SectionCard("AI 配置（DeepSeek）", Icons.Filled.AutoAwesome) {
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it },
                    label = { Text("API Base URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("模型名称（默认 deepseek-flash）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("流式输出", modifier = Modifier.weight(1f))
                    Switch(checked = stream, onCheckedChange = { stream = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = timeout, onValueChange = { timeout = it.filter { c -> c.isDigit() } },
                        label = { Text("超时(秒)") }, modifier = Modifier.weight(1f), singleLine = true,
                    )
                    OutlinedTextField(
                        value = maxTokens, onValueChange = { maxTokens = it.filter { c -> c.isDigit() } },
                        label = { Text("最大输出 token") }, modifier = Modifier.weight(1f), singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    label = { Text("提示词（默认：详细讲解内容）") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        vm.saveAi(
                            ai.copy(
                                apiKey = apiKey.trim(),
                                baseUrl = baseUrl.trim(),
                                model = model.trim(),
                                stream = stream,
                                timeoutSeconds = timeout.toIntOrNull() ?: 180,
                                maxTokens = maxTokens.toIntOrNull() ?: 4096,
                                prompt = prompt,
                            ),
                        )
                    }) { Text("保存 AI 配置") }
                    Button(onClick = { vm.testAi() }, enabled = !aiTesting) {
                        Text("测试连接")
                    }
                }
                if (aiTesting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(16.dp).height(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("测试中…", style = MaterialTheme.typography.labelMedium)
                    }
                }
                aiTestResult?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "用户文字备注不会发送给 AI；仅发送标记图片和提示词。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ===== 导出/导入 =====
            SectionCard("数据备份（导出 / 导入）", Icons.Filled.CloudSync) {
                Text(
                    "导出为 zip（包含数据库、录音、图片、课程表与 AI 配置，含 API Key 且不加密）。导入为合并模式：文件 Hash 相同跳过，不同则新增并追加“_导入_N”后缀。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val ts = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                        exportLauncher.launch("backup_$ts.zip")
                    }) { Text("导出全部数据") }
                    Button(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                        Text("导入备份")
                    }
                }
                backupProgress?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // ===== 存储管理 =====
            SectionCard("存储管理", Icons.Filled.Storage) {
                storage?.let { (rec, img) ->
                    Text(
                        "录音占用：${Formatters.bytes(rec)}\n图片占用：${Formatters.bytes(img)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.clearTempFiles() }) { Text("清理临时文件") }
                }
                OutlinedTextField(
                    value = sizeLimit,
                    onValueChange = { sizeLimit = it.filter { c -> c.isDigit() } },
                    label = { Text("录音文件大小上限（MB，0 = 不限制）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("压缩标记图片后再上传")
                        Text(
                            "默认发送原图；开启后以 JPEG 质量 ${compressQuality.toInt()}% 压缩",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = compress, onCheckedChange = { compress = it })
                }
                if (compress) {
                    Text("压缩质量：${compressQuality.toInt()}%", style = MaterialTheme.typography.labelMedium)
                    androidx.compose.material3.Slider(
                        value = compressQuality,
                        onValueChange = { compressQuality = it },
                        valueRange = 30f..100f,
                    )
                }
                Button(onClick = {
                    vm.saveGeneral(
                        general.copy(
                            sizeLimitMb = sizeLimit.toIntOrNull() ?: 0,
                            compressImages = compress,
                            compressQuality = compressQuality.toInt(),
                        ),
                    )
                    vm.refreshStorage()
                }) { Text("保存存储设置") }
            }

            // ===== 权限与保活 =====
            SectionCard("权限与保活", Icons.Filled.Shield) {
                Text(
                    "为防止录音被系统杀死，建议忽略电池优化并允许后台运行。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.requestIgnoreBatteryOptimization() }) { Text("忽略电池优化") }
                    Button(onClick = { vm.openAppWhitelistSettings() }) { Text("应用详情设置") }
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    Text(
                        "通知权限用于显示录音常驻通知，若未允许请在系统设置中开启。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ===== 相机设置 =====
            SectionCard("相机设置", Icons.Filled.PhotoCamera) {
                Text(
                    "系统相机兼容性好；若录音中调用系统相机导致录音中断，请切换为应用内相机。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = general.cameraMode == CameraMode.SYSTEM,
                        onClick = { vm.saveGeneral(general.copy(cameraMode = CameraMode.SYSTEM)) },
                    )
                    Text("系统相机（默认）")
                    Spacer(Modifier.width(16.dp))
                    RadioButton(
                        selected = general.cameraMode == CameraMode.IN_APP,
                        onClick = { vm.saveGeneral(general.copy(cameraMode = CameraMode.IN_APP)) },
                    )
                    Text("应用内相机")
                }
            }

            // ===== 检查更新 =====
            SectionCard("应用更新（当前 v${UpdateConfig.CURRENT_VERSION}）", Icons.Filled.SystemUpdate) {
                when (val s = updateState) {
                    is UpdateUiState.Idle -> Button(onClick = { vm.checkUpdate() }) { Text("检查更新") }
                    UpdateUiState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(16.dp).height(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("正在检查更新…")
                    }
                    is UpdateUiState.UpToDate -> {
                        Text("已是最新版本 v${s.version}", color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { vm.checkUpdate() }) { Text("再次检查") }
                    }
                    is UpdateUiState.Available -> {
                        Text("发现新版本 ${s.info.version}（${s.info.releaseName}）", color = MaterialTheme.colorScheme.primary)
                        if (s.info.changelog.isNotBlank()) {
                            Text(s.info.changelog, style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.downloadUpdate(s.info) }) { Text("下载并安装") }
                            TextButton(onClick = { vm.resetUpdateState() }) { Text("忽略") }
                        }
                    }
                    is UpdateUiState.Downloading -> {
                        Text("下载中 ${s.percent}%")
                        LinearProgressIndicator(
                            progress = { s.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is UpdateUiState.ReadyToInstall -> {
                        Text("下载完成，点击安装", color = MaterialTheme.colorScheme.primary)
                        Button(onClick = { vm.installUpdate(s.file) }) { Text("安装 APK") }
                    }
                    is UpdateUiState.Error -> {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { vm.checkUpdate() }) { Text("重试") }
                    }
                }
            }

            // ===== 关于 =====
            SectionCard("关于", Icons.Filled.Info) {
                Text("随堂录 v${UpdateConfig.CURRENT_VERSION}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "所有数据（录音、图片、AI 回复）保存在应用私有目录，卸载应用会全部删除，请定期在上方导出备份。" +
                        "API Key 仅用于调用你配置的 AI 服务，导出备份时会明文包含，请妥善保管备份文件。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    backupResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.consumeBackupResult() },
            title = { Text("操作结果") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.consumeBackupResult() }) { Text("好的") } },
        )
    }
}

@Composable
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}
