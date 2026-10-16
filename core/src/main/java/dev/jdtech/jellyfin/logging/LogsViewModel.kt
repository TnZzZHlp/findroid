package dev.jdtech.jellyfin.logging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.settings.domain.logging.LogEntry
import dev.jdtech.jellyfin.settings.domain.logging.LogLevel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class LogsUiState(
    val snapshot: LogSnapshot = LogSnapshot(),
    val settings: LogSettings = LogSettings(),
    val query: String = "",
    val minimumLevel: LogLevel? = null,
    val entries: List<LogEntry> = emptyList(),
)

@HiltViewModel
class LogsViewModel
@Inject
constructor(
    private val logger: DiagnosticsLogger,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val state =
        combine(
                logger.snapshot,
                logger.settings,
                savedStateHandle.getStateFlow("log_query", ""),
                savedStateHandle.getStateFlow<String?>("log_level", null),
            ) { snapshot, settings, query, levelName ->
                val level = LogLevel.entries.firstOrNull { it.name == levelName }
                LogsUiState(
                    snapshot = snapshot,
                    settings = settings,
                    query = query,
                    minimumLevel = level,
                    entries =
                        snapshot.entries.filter {
                            (level == null || it.level.priority >= level.priority) &&
                                (query.isBlank() ||
                                    it.message.contains(query, true) ||
                                    it.tag.contains(query, true))
                        },
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LogsUiState())

    fun setQuery(query: String) {
        savedStateHandle["log_query"] = query
    }

    fun setMinimumLevel(level: LogLevel?) {
        savedStateHandle["log_level"] = level?.name
    }

    fun setEnabled(enabled: Boolean) = logger.setEnabled(enabled)

    fun setDetailed(detailed: Boolean) = logger.setDetailed(detailed)

    suspend fun clearLogs() = logger.clear()

    suspend fun export(): File = logger.export()
}
