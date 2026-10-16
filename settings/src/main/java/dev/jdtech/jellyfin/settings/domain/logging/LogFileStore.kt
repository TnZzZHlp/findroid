package dev.jdtech.jellyfin.settings.domain.logging

import java.io.File
import java.io.IOException
import java.util.Base64

class LogFileStore(
    private val directory: File,
    private val maxBytes: Long = 10L * 1024 * 1024,
    private val retentionMillis: Long = 7L * 24 * 60 * 60 * 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val chunkBytes = minOf(256L * 1024, maxBytes / 4)
    private var lastPruned = Long.MIN_VALUE

    init {
        require(maxBytes >= 1024)
        require(retentionMillis > 0)
    }

    @Synchronized
    fun append(entry: LogEntry): LogEntry {
        ensureDirectory()
        val message = LogSanitizer.sanitize(entry.message)
        val messageLimit = minOf(8000, (maxBytes / 16).toInt())
        val sanitized =
            entry.copy(
                tag =
                    LogSanitizer.sanitize(entry.tag).replace('\n', ' ').replace('\r', ' ').take(80),
                message =
                    if (message.length > messageLimit) message.take(messageLimit) + "\n[truncated]"
                    else message,
            )
        val bytes = (encode(sanitized) + "\n").toByteArray(Charsets.UTF_8)
        val files = files()
        val latest = files.lastOrNull()
        val target =
            if (latest != null && latest.length() + bytes.size <= chunkBytes) latest
            else {
                val id = maxOf(clock(), (latest?.nameWithoutExtension?.toLongOrNull() ?: 0L) + 1)
                File(directory, "${id.toString().padStart(19, '0')}.log")
            }
        target.appendBytes(bytes)
        target.setLastModified(clock())
        trimSize()
        prune()
        return sanitized
    }

    @Synchronized
    fun recent(limit: Int = 500): List<LogEntry> {
        require(limit > 0)
        prune()
        val cutoff = clock() - retentionMillis
        val result = ArrayList<LogEntry>()
        for (file in files().asReversed()) {
            for (line in file.readLines().asReversed()) {
                val entry = decode(line) ?: continue
                if (entry.timestamp >= cutoff) result.add(entry)
                if (result.size == limit) return result
            }
        }
        return result
    }

    @Synchronized
    fun exportTo(destination: File) {
        prune(force = true)
        val cutoff = clock() - retentionMillis
        destination.bufferedWriter().use { writer ->
            writer.appendLine("Findroid diagnostic logs (credentials and paths filtered)")
            for (file in files()) {
                file.useLines { lines ->
                    lines.forEach { line ->
                        val entry = decode(line)
                        if (entry != null && entry.timestamp >= cutoff) {
                            writer.appendLine(entry.formatted())
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    fun clear() {
        files().forEach { file ->
            if (!file.delete()) throw IOException("Unable to clear diagnostic logs")
        }
    }

    @Synchronized
    fun prune(force: Boolean = false) {
        val now = clock()
        if (!force && lastPruned != Long.MIN_VALUE && now - lastPruned in 0 until 60_000) return
        val cutoff = now - retentionMillis
        files().forEach { file ->
            if (file.lastModified() < cutoff) {
                if (!file.delete()) throw IOException("Unable to expire diagnostic logs")
            } else {
                val lines = file.readLines()
                val retained = lines.filter { (decode(it)?.timestamp ?: Long.MIN_VALUE) >= cutoff }
                if (retained.size != lines.size) {
                    if (retained.isEmpty()) {
                        if (!file.delete()) throw IOException("Unable to expire diagnostic logs")
                    } else {
                        file.writeText(retained.joinToString("\n", postfix = "\n"))
                    }
                }
            }
        }
        trimSize()
        lastPruned = now
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Unable to create diagnostic log directory")
        }
    }

    private fun files(): List<File> =
        directory
            .listFiles { file -> file.isFile && file.name.matches(Regex("[0-9]{19}\\.log")) }
            ?.sortedBy { it.name } ?: emptyList()

    private fun trimSize() {
        val files = files()
        var bytes = files.sumOf { it.length() }
        for (file in files) {
            if (bytes <= maxBytes) break
            val size = file.length()
            if (!file.delete()) throw IOException("Unable to rotate diagnostic logs")
            bytes -= size
        }
    }

    private fun encode(entry: LogEntry): String {
        val encoder = Base64.getEncoder()
        return listOf(
                entry.timestamp.toString(),
                entry.level.name,
                encoder.encodeToString(entry.tag.toByteArray(Charsets.UTF_8)),
                encoder.encodeToString(entry.message.toByteArray(Charsets.UTF_8)),
            )
            .joinToString("\t")
    }

    private fun decode(line: String): LogEntry? = runCatching {
        val parts = line.split('\t', limit = 4)
        val decoder = Base64.getDecoder()
        LogEntry(
            parts[0].toLong(),
            LogLevel.valueOf(parts[1]),
            String(decoder.decode(parts[2]), Charsets.UTF_8),
            String(decoder.decode(parts[3]), Charsets.UTF_8),
        )
    }
        .getOrNull()
}
