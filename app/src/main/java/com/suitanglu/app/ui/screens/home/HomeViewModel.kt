package com.suitanglu.app.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suitanglu.app.AppGraph
import com.suitanglu.app.data.Formatters
import com.suitanglu.app.data.db.Marker
import com.suitanglu.app.data.db.Recording
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeItem(
    val recording: Recording,
    val markerTotal: Int,
    val markerSuccess: Int,
    val markerFailed: Int,
    val subtitle: String,
)

data class HomeSection(val label: String, val items: List<HomeItem>)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppGraph.get(app)
    private val repo = container.recordingRepository

    val sections: StateFlow<List<HomeSection>> =
        combine(container.db.recordingDao().observeActive(), container.db.markerDao().observeAll()) { recs, markers ->
            val byRecording = markers.groupBy { it.recordingId }
            val items = recs.map { rec ->
                val ms = byRecording[rec.id].orEmpty()
                val success = ms.count { it.aiStatus == Marker.AI_SUCCESS }
                val failed = ms.count { it.aiStatus == Marker.AI_FAILED }
                HomeItem(
                    recording = rec,
                    markerTotal = ms.size,
                    markerSuccess = success,
                    markerFailed = failed,
                    subtitle = repo.recordingSubtitle(rec, ms),
                )
            }
            items.groupBy { Formatters.dateLabel(it.recording.startTime) }
                .map { (label, list) -> HomeSection(label, list) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val firstDeleteTipShown: StateFlow<Boolean> = container.settings.generalConfig
        .map { it.firstDeleteTipShown }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun rename(id: Long, name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repo.rename(id, name)) }
    }

    fun deleteToRecycle(id: Long) {
        viewModelScope.launch { repo.deleteToRecycle(id) }
    }

    fun deleteToRecycleMany(ids: Collection<Long>) {
        viewModelScope.launch { ids.forEach { repo.deleteToRecycle(it) } }
    }

    fun deleteAudioOnly(id: Long) {
        viewModelScope.launch { repo.deleteAudioOnly(id) }
    }

    fun markDeleteTipShown() {
        viewModelScope.launch {
            val g = container.settings.generalConfigNow()
            container.settings.setGeneralConfig(g.copy(firstDeleteTipShown = true))
        }
    }
}
