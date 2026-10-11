package com.suitanglu.app.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.suitanglu.app.AppGraph
import com.suitanglu.app.data.CourseSchedule
import com.suitanglu.app.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = AppGraph.get(context)
    val scope = rememberCoroutineScope()
    var selectedDay by remember { mutableIntStateOf(1) }
    var names by remember { mutableStateOf(mapOf<Pair<Int, Int>, String>()) }
    var slots by remember { mutableStateOf(SettingsRepository.DEFAULT_SLOTS) }
    var startTexts by remember { mutableStateOf(mapOf<Int, String>()) }
    var endTexts by remember { mutableStateOf(mapOf<Int, String>()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        names = container.settings.allCourseNames().first()
        loaded = true
    }

    LaunchedEffect(selectedDay) {
        val s = container.settings.slotsNow(selectedDay)
        slots = s
        startTexts = s.associate { it.index to minutesToText(it.startMinute) }
        endTexts = s.associate { it.index to minutesToText(it.endMinute) }
        error = null
    }

    fun save(onSaved: () -> Unit) {
        val newSlots = mutableListOf<com.suitanglu.app.data.DaySlotTime>()
        for (slot in slots) {
            val start = parseHHmm(startTexts[slot.index].orEmpty())
            val end = parseHHmm(endTexts[slot.index].orEmpty())
            if (start == null || end == null) {
                error = "时间段格式应为 HH:mm，例如 08:00"
                return
            }
            if (start >= end) {
                error = "第${slot.index}节的开始时间必须早于结束时间"
                return
            }
            newSlots.add(com.suitanglu.app.data.DaySlotTime(slot.index, start, end))
        }
        scope.launch {
            for (s in newSlots) {
                container.settings.setSlotTime(selectedDay, s.index, s.startMinute, s.endMinute)
            }
            container.settings.setAllCourseNames(names)
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课程表") },
                navigationIcon = {
                    IconButton(onClick = { save { onBack() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
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
            Text(
                "按星期几配置课程名称与时间段；时间段起止可修改（每天独立）。课程名可为空，为空或录音未命中时间段时显示“未命名”。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CourseSchedule.dayNames().forEachIndexed { index, label ->
                    val day = index + 1
                    FilterChip(
                        selected = selectedDay == day,
                        onClick = { selectedDay = day },
                        label = { Text(label) },
                    )
                }
            }
            if (loaded) {
                slots.forEach { slot ->
                    val key = selectedDay to slot.index
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "第${slot.index}节",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = startTexts[slot.index].orEmpty(),
                                    onValueChange = { startTexts = startTexts + (slot.index to it) },
                                    label = { Text("开始") },
                                    modifier = Modifier.width(110.dp),
                                    singleLine = true,
                                    placeholder = { Text("HH:mm") },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = endTexts[slot.index].orEmpty(),
                                    onValueChange = { endTexts = endTexts + (slot.index to it) },
                                    label = { Text("结束") },
                                    modifier = Modifier.width(110.dp),
                                    singleLine = true,
                                    placeholder = { Text("HH:mm") },
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = names[key].orEmpty(),
                                onValueChange = { v -> names = names + (key to v) },
                                label = { Text("课程名称（可为空）") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                        }
                    }
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { save { onBack() } },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("保存课程表") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            for (s in SettingsRepository.DEFAULT_SLOTS) {
                                container.settings.setSlotTime(selectedDay, s.index, s.startMinute, s.endMinute)
                            }
                            val fresh = container.settings.slotsNow(selectedDay)
                            slots = fresh
                            startTexts = fresh.associate { it.index to minutesToText(it.startMinute) }
                            endTexts = fresh.associate { it.index to minutesToText(it.endMinute) }
                            error = null
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("恢复默认时段") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun parseHHmm(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

private fun minutesToText(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
