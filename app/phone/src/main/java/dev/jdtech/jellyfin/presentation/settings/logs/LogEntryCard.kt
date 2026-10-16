package dev.jdtech.jellyfin.presentation.settings.logs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.settings.domain.logging.LogEntry
import dev.jdtech.jellyfin.settings.domain.logging.LogLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logTimeFormat =
    DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

@Composable
internal fun LogEntryCard(entry: LogEntry, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text =
                    "${logTimeFormat.format(Instant.ofEpochMilli(entry.timestamp))} ${entry.level.name} · ${entry.tag}",
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (entry.level.priority >= LogLevel.WARN.priority)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
            )
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun LogEntryDialog(entry: LogEntry, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${entry.level.name} · ${entry.tag}") },
        text = {
            SelectionContainer {
                Text(
                    entry.formatted(),
                    modifier =
                        Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) { Text(stringResource(R.string.logs_copy)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.close)) }
        },
    )
}
