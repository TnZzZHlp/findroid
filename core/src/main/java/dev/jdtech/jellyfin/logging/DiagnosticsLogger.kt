package dev.jdtech.jellyfin.logging

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.settings.domain.logging.LogEntry
import dev.jdtech.jellyfin.settings.domain.logging.LogFileStore
import dev.jdtech.jellyfin.settings.domain.logging.LogLevel
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class LogSettings(val enabled: Boolean = true, val detailed: Boolean = false)

data class LogSnapshot(
    val entries: List<LogEntry> = emptyList(),
    val loading: Boolean = true,
    val storageError: Boolean = false,
    val dropped: Long = 0,
)

@Singleton
class DiagnosticsLogger
@Inject
constructor(
    @ApplicationContext context: Context,
    private val preferences: SharedPreferences,
) {
    private val store = LogFileStore(File(context.noBackupFilesDir, "diagnostic-logs"))
    private val exportDirectory = File(context.cacheDir, "log-exports")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<Command>(256)
    private val dropped = AtomicLong()
    private var started = false
    private var writeError = false
    private val _settings =
        MutableStateFlow(
            LogSettings(
                preferences.getBoolean("diagnostics_enabled", true),
                preferences.getBoolean("diagnostics_detailed", false),
            )
        )
    val settings = _settings.asStateFlow()
    private val _snapshot = MutableStateFlow(LogSnapshot())
    val snapshot = _snapshot.asStateFlow()

    init {
        scope.launch {
            refresh()
            for (command in commands) {
                execute(command)
                delay(100)
                for (index in 0 until 63) {
                    val next = commands.tryReceive().getOrNull() ?: break
                    execute(next)
                }
                refresh()
            }
        }
        scope.launch {
            while (true) {
                delay(60_000)
                commands.trySend(Command.Refresh)
            }
        }
    }

    fun start(version: String) {
        if (started) return
        started = true
        Timber.plant(
            object : Timber.DebugTree() {
                override fun isLoggable(tag: String?, priority: Int): Boolean = accepts(priority)

                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    val level = LogLevel.entries.firstOrNull { it.priority == priority } ?: return
                    val entry =
                        LogEntry(
                            System.currentTimeMillis(),
                            level,
                            tag ?: "App",
                            message.take(32_000),
                        )
                    if (commands.trySend(Command.Record(entry)).isFailure) dropped.incrementAndGet()
                }
            }
        )
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (settings.value.enabled) {
                    runCatching {
                        store.append(
                            LogEntry(
                                System.currentTimeMillis(),
                                LogLevel.ASSERT,
                                "Crash",
                                "Uncaught exception on ${thread.name}\n${throwable.stackTraceToString()}",
                            )
                        )
                    }
                }
            } finally {
                if (previous != null) previous.uncaughtException(thread, throwable)
                else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
        }
        Timber.i("Application started: %s", version)
    }

    fun setEnabled(enabled: Boolean) {
        _settings.value = settings.value.copy(enabled = enabled)
        preferences.edit().putBoolean("diagnostics_enabled", enabled).apply()
    }

    fun setDetailed(detailed: Boolean) {
        _settings.value = settings.value.copy(detailed = detailed)
        preferences.edit().putBoolean("diagnostics_detailed", detailed).apply()
    }

    suspend fun clear() {
        val result = CompletableDeferred<Unit>()
        commands.send(Command.Clear(result))
        result.await()
    }

    suspend fun export(): File {
        val result = CompletableDeferred<File>()
        commands.send(Command.Export(result))
        return result.await()
    }

    private fun accepts(priority: Int): Boolean =
        settings.value.let { it.enabled && (it.detailed || priority >= LogLevel.INFO.priority) }

    private fun execute(command: Command) {
        try {
            when (command) {
                is Command.Record ->
                    if (accepts(command.entry.level.priority)) {
                        store.append(command.entry)
                        writeError = false
                    }
                is Command.Clear -> {
                    store.clear()
                    exportDirectory.listFiles()?.forEach { file -> check(file.delete()) }
                    writeError = false
                    dropped.set(0)
                    command.result.complete(Unit)
                }
                is Command.Export -> {
                    check(exportDirectory.isDirectory || exportDirectory.mkdirs())
                    pruneExports(keep = 2)
                    val file = File.createTempFile("findroid-logs-", ".txt", exportDirectory)
                    try {
                        store.exportTo(file)
                        command.result.complete(file)
                    } catch (e: Exception) {
                        file.delete()
                        throw e
                    }
                }
                Command.Refresh -> store.prune(force = true)
            }
        } catch (e: Exception) {
            writeError = true
            _snapshot.value = snapshot.value.copy(loading = false, storageError = true)
            when (command) {
                is Command.Clear -> command.result.completeExceptionally(e)
                is Command.Export -> command.result.completeExceptionally(e)
                else -> Unit
            }
        }
    }

    private fun pruneExports(keep: Int = 3) {
        val now = System.currentTimeMillis()
        exportDirectory
            .listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.forEachIndexed { index, file ->
                if (index >= keep || now - file.lastModified() > 24 * 60 * 60 * 1000L)
                    check(file.delete())
            }
    }

    private fun refresh() {
        try {
            pruneExports()
            _snapshot.value =
                LogSnapshot(
                    entries = store.recent(),
                    loading = false,
                    storageError = writeError,
                    dropped = dropped.get(),
                )
        } catch (_: Exception) {
            _snapshot.value =
                snapshot.value.copy(loading = false, storageError = true, dropped = dropped.get())
        }
    }

    private sealed interface Command {
        data class Record(val entry: LogEntry) : Command

        data class Clear(val result: CompletableDeferred<Unit>) : Command

        data class Export(val result: CompletableDeferred<File>) : Command

        data object Refresh : Command
    }
}
