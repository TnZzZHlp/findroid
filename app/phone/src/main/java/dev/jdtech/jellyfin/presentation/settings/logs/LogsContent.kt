package dev.jdtech.jellyfin.presentation.settings.logs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.logging.LogsUiState
import dev.jdtech.jellyfin.presentation.utils.plus
import dev.jdtech.jellyfin.settings.domain.logging.LogEntry
import dev.jdtech.jellyfin.settings.domain.logging.LogLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogsContent(
    state: LogsUiState,
    busy: Boolean,
    navigateBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDetailedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onLevelChange: (LogLevel?) -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onCopy: (LogEntry) -> Unit,
) {
    var selectedEntry by remember { mutableStateOf<LogEntry?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            painterResource(CoreR.drawable.ic_arrow_left),
                            stringResource(R.string.logs_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onClear, enabled = !busy && !state.snapshot.loading) {
                        Icon(
                            painterResource(CoreR.drawable.ic_trash),
                            stringResource(R.string.logs_clear),
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogSwitch(
                        stringResource(R.string.logs_enabled),
                        state.settings.enabled,
                        onEnabledChange,
                    )
                    LogSwitch(
                        stringResource(R.string.logs_detailed),
                        state.settings.detailed,
                        onDetailedChange,
                        enabled = state.settings.enabled,
                    )
                    Text(
                        stringResource(R.string.logs_retention),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(R.string.logs_privacy),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!state.settings.enabled) {
                        Text(
                            stringResource(R.string.logs_disabled),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onShare,
                            enabled = !busy && !state.snapshot.loading,
                        ) {
                            Text(stringResource(CoreR.string.share))
                        }
                        OutlinedButton(
                            onClick = onSave,
                            enabled = !busy && !state.snapshot.loading,
                        ) {
                            Text(stringResource(R.string.logs_save))
                        }
                    }
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.logs_search)) },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = state.minimumLevel == null,
                            onClick = { onLevelChange(null) },
                            label = { Text(stringResource(R.string.logs_all_levels)) },
                        )
                        LogLevel.entries.forEach { level ->
                            FilterChip(
                                selected = state.minimumLevel == level,
                                onClick = { onLevelChange(level) },
                                label = { Text("${level.name}+") },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.logs_recent, state.entries.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (busy || state.snapshot.loading) {
                item { CircularProgressIndicator() }
            }
            if (state.snapshot.storageError) {
                item {
                    Text(
                        stringResource(R.string.logs_storage_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (state.snapshot.dropped > 0) {
                item { Text(stringResource(R.string.logs_dropped, state.snapshot.dropped)) }
            }
            if (state.entries.isEmpty() && !state.snapshot.loading) {
                item { Text(stringResource(R.string.logs_empty)) }
            }
            items(state.entries) { entry ->
                LogEntryCard(entry, onClick = { selectedEntry = entry })
            }
        }
    }
    selectedEntry?.let { entry ->
        LogEntryDialog(entry, onCopy = { onCopy(entry) }, onDismiss = { selectedEntry = null })
    }
}

@Composable
private fun LogSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
