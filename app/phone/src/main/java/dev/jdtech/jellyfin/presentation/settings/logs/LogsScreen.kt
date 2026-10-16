package dev.jdtech.jellyfin.presentation.settings.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.logging.LogsViewModel
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LogsScreen(navigateBack: () -> Unit, viewModel: LogsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var exportAction by remember { mutableStateOf<String?>(null) }
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    val saveDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
            uri ->
            val path = pendingExport
            pendingExport = null
            if (uri != null && path != null) {
                scope.launch {
                    busy = true
                    try {
                        LogExport.save(context, File(path), uri)
                        Toast.makeText(context, R.string.logs_saved, Toast.LENGTH_SHORT).show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.logs_operation_failed, Toast.LENGTH_LONG)
                            .show()
                    } finally {
                        busy = false
                    }
                }
            }
        }

    LogsContent(
        state = state,
        busy = busy,
        navigateBack = navigateBack,
        onEnabledChange = viewModel::setEnabled,
        onDetailedChange = viewModel::setDetailed,
        onQueryChange = viewModel::setQuery,
        onLevelChange = viewModel::setMinimumLevel,
        onClear = { clearDialog = true },
        onShare = { exportAction = "share" },
        onSave = { exportAction = "save" },
        onCopy = { entry ->
            context
                .getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Findroid log", entry.formatted()))
            Toast.makeText(context, R.string.logs_copied, Toast.LENGTH_SHORT).show()
        },
    )

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(stringResource(R.string.logs_clear)) },
            text = { Text(stringResource(R.string.logs_clear_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearDialog = false
                        scope.launch {
                            busy = true
                            try {
                                viewModel.clearLogs()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Toast.makeText(
                                        context,
                                        R.string.logs_operation_failed,
                                        Toast.LENGTH_LONG,
                                    )
                                    .show()
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.logs_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDialog = false }) {
                    Text(stringResource(CoreR.string.cancel))
                }
            },
        )
    }

    if (exportAction != null) {
        AlertDialog(
            onDismissRequest = { exportAction = null },
            title = { Text(stringResource(R.string.logs_export)) },
            text = { Text(stringResource(R.string.logs_export_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val action = exportAction
                        exportAction = null
                        scope.launch {
                            busy = true
                            try {
                                val file = viewModel.export()
                                if (action == "share") LogExport.share(context, file)
                                else {
                                    pendingExport = file.absolutePath
                                    saveDocument.launch(file.name)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                pendingExport = null
                                Toast.makeText(
                                        context,
                                        R.string.logs_operation_failed,
                                        Toast.LENGTH_LONG,
                                    )
                                    .show()
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.logs_export))
                }
            },
            dismissButton = {
                TextButton(onClick = { exportAction = null }) {
                    Text(stringResource(CoreR.string.cancel))
                }
            },
        )
    }
}
