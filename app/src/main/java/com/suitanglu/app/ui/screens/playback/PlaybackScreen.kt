package com.suitanglu.app.ui.screens.playback

import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext as Ctx1
import coil.compose.AsyncImage
import com.suitanglu.app.data.Formatters
import com.suitanglu.app.data.db.Marker
import com.suitanglu.app.data.db.PauseEvent
import com.suitanglu.app.data.db.Recording
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(recordingId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: PlaybackViewModel = viewModel(
        key = "playback_$recordingId",
        factory = viewModelFactory {
            initializer {
                PlaybackViewModel(context.applicationContext as android.app.Application, recordingId)
            }
        },
    )
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val recording by vm.recording.collectAsState()
    val markers by vm.markers.collectAsState()
    val pauseEvents by vm.pauseEvents.collectAsState()
    val player by vm.player.collectAsState()
    val position by vm.position.collectAsState()
    val duration by vm.duration.collectAsState()
    val playing by vm.playing.collectAsState()
    val speed by vm.speed.collectAsState()
    val sendResult by vm.sendResult.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val playerError by vm.playerError.collectAsState()

    var expandedMarkerId by remember { mutableStateOf<Long?>(null) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    LaunchedEffect(sendResult) {
        sendResult?.let {
            snackbar.showSnackbar(it)
            vm.consumeSendResult()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(recording?.title ?: "录音回放", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val rec = recording
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (rec == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                return@LazyColumn
            }

            // 播放器
            item {
                Column(Modifier.padding(16.dp)) {
                    if (rec.status == Recording.STATUS_AUDIO_DELETED) {
                        Text(
                            "音频已删除",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "${Formatters.dateTime(rec.startTime)} · ${Formatters.duration(rec.durationMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (playerError != null) {
                        Text(
                            "音频无法播放：$playerError",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "${Formatters.dateTime(rec.startTime)} · ${Formatters.duration(rec.durationMs)}\n标记与 AI 讲解仍可查看。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (player == null) {
                        Text("音频文件不存在或正在准备…", color = MaterialTheme.colorScheme.error)
                    } else {
                        MarkerProgressBar(
                            durationMs = duration.takeIf { it > 0 } ?: rec.durationMs,
                            positionMs = position,
                            markers = markers,
                            pauseEvents = pauseEvents,
                            onSeek = { vm.seekTo(it) },
                            onMarkerClick = { m ->
                                vm.jumpToMarker(m)
                                expandedMarkerId = m.id
                            },
                            onPausePointClick = { event ->
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (event.type == PauseEvent.TYPE_USER_PAUSE) "此处为手动暂停"
                                        else "此处因${event.reason ?: "来电/其他应用打断"}，录音暂停",
                                    )
                                }
                            },
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { vm.skip(-15_000) }) {
                                Icon(Icons.Filled.FastRewind, contentDescription = "后退15秒")
                            }
                            IconButton(
                                onClick = { vm.togglePlay() },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            ) {
                                Icon(
                                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playing) "暂停" else "播放",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                            IconButton(onClick = { vm.skip(15_000) }) {
                                Icon(Icons.Filled.FastForward, contentDescription = "前进15秒")
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${Formatters.clockTime(position)} / ${Formatters.clockTime(duration.takeIf { it > 0 } ?: rec.durationMs)}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Box {
                                AssistChip(
                                    onClick = { showSpeedMenu = true },
                                    label = { Text("${speed}x") },
                                )
                                androidx.compose.material3.DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false },
                                ) {
                                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("${v}x") },
                                            onClick = {
                                                vm.setSpeed(v)
                                                showSpeedMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 重发按钮
            item {
                val unsent = markers.count { it.aiStatus != Marker.AI_SUCCESS }
                if (unsent > 0) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { vm.resendFailed() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重发失败")
                        }
                        OutlinedButton(onClick = { vm.resendAll() }) {
                            Icon(Icons.Filled.Replay, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("全部重发")
                        }
                        Text(
                            "$unsent 个标记未发送",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // 标记列表
            item {
                Text(
                    "标记（按时间排序）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            if (markers.isEmpty()) {
                item {
                    Text(
                        "本条录音没有标记",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(markers, key = { it.id }) { marker ->
                MarkerCard(
                    marker = marker,
                    expanded = expandedMarkerId == marker.id,
                    streamingText = streaming?.takeIf { it.markerId == marker.id }?.text,
                    onClick = {
                        vm.jumpToMarker(marker)
                        expandedMarkerId = if (expandedMarkerId == marker.id) null else marker.id
                    },
                    onToggleExpand = {
                        expandedMarkerId = if (expandedMarkerId == marker.id) null else marker.id
                    },
                    onResend = { vm.resendOne(marker.id) },
                    loadImages = { vm.imagesOf(marker) },
                )
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun MarkerProgressBar(
    durationMs: Long,
    positionMs: Long,
    markers: List<Marker>,
    pauseEvents: List<PauseEvent>,
    onSeek: (Long) -> Unit,
    onMarkerClick: (Marker) -> Unit,
    onPausePointClick: (PauseEvent) -> Unit,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val playedColor = com.suitanglu.app.ui.theme.BrandAccent()
    val successColor = MaterialTheme.colorScheme.primary
    val failedColor = MaterialTheme.colorScheme.error
    val pendingColor = MaterialTheme.colorScheme.tertiary
    val pauseColor = Color(0xFFFF9800)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val barHeight = with(density) { 8.dp.toPx() }
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val badgeBg = MaterialTheme.colorScheme.inverseSurface
    val badgeText = MaterialTheme.colorScheme.inverseOnSurface

    val totalMs = durationMs.coerceAtLeast(1)

    // 相邻标记按比例 2% 聚合成簇，密集时不再糊成一串
    val clusters = remember(markers, totalMs) {
        val result = mutableListOf<MutableList<Marker>>()
        for (m in markers.sortedBy { it.offsetMs }) {
            val last = result.lastOrNull()
            if (last != null && (m.offsetMs - last.first().offsetMs).toFloat() / totalMs < 0.02f) {
                last.add(m)
            } else {
                result.add(mutableListOf(m))
            }
        }
        result
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pointerInput(clusters, pauseEvents, totalMs) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    val ms = (fraction * totalMs).toLong()
                    // 命中标记簇（容差 4%），簇内取离点击最近的标记
                    val cluster = clusters.firstOrNull { c ->
                        val cxFraction = (c.sumOf { it.offsetMs }.toFloat() / c.size) / totalMs
                        kotlin.math.abs(cxFraction - fraction) < 0.04f
                    }
                    if (cluster != null) {
                        onMarkerClick(
                            cluster.minByOrNull {
                                kotlin.math.abs(it.offsetMs.toFloat() / totalMs - fraction)
                            }!!,
                        )
                        return@detectTapGestures
                    }
                    val pauseEvent = pauseEvents.firstOrNull {
                        val mxFraction = it.offsetMs.toFloat() / totalMs
                        kotlin.math.abs(mxFraction - fraction) < 0.04f
                    }
                    if (pauseEvent != null) {
                        onPausePointClick(pauseEvent)
                        return@detectTapGestures
                    }
                    onSeek(ms)
                }
            },
    ) {
        val y = size.height / 2 - 4.dp.toPx()
        // 分钟刻度（间隔足够时才画）
        val totalMinutes = (totalMs / 60_000).toInt()
        if (totalMinutes in 3..240) {
            val gapPx = size.width / totalMinutes
            if (gapPx > 20f) {
                val tickColor = trackColor.copy(alpha = 0.9f)
                for (i in 1 until totalMinutes) {
                    val x = gapPx * i
                    drawLine(
                        color = tickColor,
                        start = Offset(x, y + barHeight / 2 + 4.dp.toPx()),
                        end = Offset(x, y + barHeight / 2 + 9.dp.toPx()),
                        strokeWidth = 2f,
                    )
                }
            }
        }
        // 轨道
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, y - barHeight / 2),
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(barHeight / 2),
        )
        // 已播放
        val playedWidth = size.width * (positionMs.toFloat() / totalMs).coerceIn(0f, 1f)
        drawRoundRect(
            color = playedColor,
            topLeft = Offset(0f, y - barHeight / 2),
            size = Size(playedWidth, barHeight),
            cornerRadius = CornerRadius(barHeight / 2),
        )
        // 暂停/打断点（菱形 + 描边）
        val borderStroke = 2.dp.toPx()
        pauseEvents.filter { it.type != PauseEvent.TYPE_RESUME }.forEach { event ->
            val x = size.width * (event.offsetMs.toFloat() / totalMs).coerceIn(0f, 1f)
            val r = barHeight * 1.3f
            rotate(45f, pivot = Offset(x, y)) {
                drawRect(
                    color = trackColor,
                    topLeft = Offset(x - r / 2 - borderStroke / 2, y - r / 2 - borderStroke / 2),
                    size = Size(r + borderStroke, r + borderStroke),
                )
                drawRect(
                    color = pauseColor,
                    topLeft = Offset(x - r / 2, y - r / 2),
                    size = Size(r, r),
                )
            }
        }
        // 标记簇（圆形 + 描边；多标记时加计数角标）
        clusters.forEach { c ->
            val cxFraction = (c.sumOf { it.offsetMs }.toFloat() / c.size) / totalMs
            val x = size.width * cxFraction.coerceIn(0f, 1f)
            val color = when {
                c.any { it.aiStatus == Marker.AI_FAILED } -> failedColor
                c.all { it.aiStatus == Marker.AI_SUCCESS } -> successColor
                else -> pendingColor
            }
            drawCircle(color = trackColor, radius = barHeight * 1.4f + borderStroke, center = Offset(x, y))
            drawCircle(color = color, radius = barHeight * 1.4f, center = Offset(x, y))
            drawCircle(color = Color.White, radius = barHeight * 0.6f, center = Offset(x, y))
            if (c.size > 1) {
                val badgeR = 8.dp.toPx()
                val badgeCenter = Offset(x, y - barHeight * 1.4f - badgeR)
                drawCircle(color = badgeBg, radius = badgeR, center = badgeCenter)
                val text = "${c.size}"
                val layout = textMeasurer.measure(
                    text,
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 10.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = badgeText,
                    ),
                )
                drawText(
                    layout,
                    topLeft = Offset(
                        badgeCenter.x - layout.size.width / 2f,
                        badgeCenter.y - layout.size.height / 2f,
                    ),
                )
            }
        }
        // 播放头（外圈光晕 + 实心）
        drawCircle(
            color = playedColor.copy(alpha = 0.25f),
            radius = barHeight * 2.6f,
            center = Offset(playedWidth, y),
        )
        drawCircle(
            color = playedColor,
            radius = barHeight * 1.8f,
            center = Offset(playedWidth, y),
        )
        drawCircle(
            color = Color.White,
            radius = barHeight * 0.7f,
            center = Offset(playedWidth, y),
        )
    }
}

@Composable
private fun MarkerCard(
    marker: Marker,
    expanded: Boolean,
    streamingText: String?,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onResend: () -> Unit,
    loadImages: suspend (Marker) -> List<com.suitanglu.app.data.db.MarkerImage>,
) {
    var images by remember(marker.id) { mutableStateOf<List<com.suitanglu.app.data.db.MarkerImage>>(emptyList()) }
    var fullImage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(expanded) {
        if (expanded) images = loadImages(marker)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(Modifier.padding(12.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Formatters.clockTime(marker.offsetMs),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                val summary = marker.userText?.take(20)
                    ?: marker.aiResponse?.take(20)
                    ?: "(无文字)"
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(marker.aiStatus)
            }
            if (marker.aiStatus == Marker.AI_FAILED && marker.aiError != null) {
                Text(
                    "失败原因：${marker.aiError}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (images.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        images.forEach { img ->
                            AsyncImage(
                                model = img.filePath,
                                contentDescription = null,
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(90.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable { fullImage = img.filePath },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                marker.userText?.let {
                    Text("备注：$it", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                when {
                    marker.aiStatus == Marker.AI_SENDING && streamingText != null -> {
                        Text("AI 讲解（生成中）…", style = MaterialTheme.typography.labelMedium)
                        Text(streamingText, style = MaterialTheme.typography.bodyMedium)
                    }
                    marker.aiStatus == Marker.AI_SENDING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("正在发送 AI 讲解请求…", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    marker.aiStatus == Marker.AI_PENDING -> {
                        Text("待发送：录音结束后将自动发送", style = MaterialTheme.typography.labelMedium)
                    }
                    marker.aiStatus == Marker.AI_SUCCESS -> {
                        var expandedText by remember(marker.id) { mutableStateOf(false) }
                        Text("AI 讲解", style = MaterialTheme.typography.labelMedium)
                        val response = marker.aiResponse.orEmpty()
                        Text(
                            response,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expandedText) Int.MAX_VALUE else 6,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        if (response.length > 120) {
                            TextButton(onClick = { expandedText = !expandedText }) {
                                Text(if (expandedText) "收起" else "展开全文")
                            }
                        }
                    }
                    marker.aiStatus == Marker.AI_FAILED -> {
                        TextButton(onClick = onResend) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重新发送")
                        }
                    }
                }
            }
        }
    }
    fullImage?.let { path ->
        Dialog(onDismissRequest = { fullImage = null }) {
            AsyncImage(
                model = path,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (label, color) = when (status) {
        Marker.AI_SUCCESS -> "成功" to MaterialTheme.colorScheme.primary
        Marker.AI_FAILED -> "失败" to MaterialTheme.colorScheme.error
        Marker.AI_SENDING -> "发送中" to MaterialTheme.colorScheme.tertiary
        else -> "待发送" to MaterialTheme.colorScheme.tertiary
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
