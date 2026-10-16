package dev.jdtech.jellyfin.settings.domain.logging

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFileStoreTest {
    @Test
    fun `stored and exported logs stay sanitized after reopening`() {
        val directory = Files.createTempDirectory("findroid-logs").toFile()
        val export = Files.createTempFile("findroid-export", ".txt").toFile()
        try {
            val now = 1_000_000L
            val store = LogFileStore(directory, clock = { now })
            store.append(
                LogEntry(
                    now,
                    LogLevel.ERROR,
                    "Player",
                    "播放失败\nhttps://media.example/video?api_key=private-key",
                )
            )
            val reopened = LogFileStore(directory, clock = { now })

            assertEquals("播放失败\n[host=media.example]", reopened.recent().single().message)
            reopened.exportTo(export)
            assertTrue(export.readText().contains("ERROR [Player] 播放失败"))
            assertFalse(export.readText().contains("private-key"))
        } finally {
            directory.deleteRecursively()
            export.delete()
        }
    }

    @Test
    fun `rotation stays within the total byte limit and retains the newest events`() {
        val directory = Files.createTempDirectory("findroid-rotation").toFile()
        try {
            var now = 1_000_000L
            val store = LogFileStore(directory, maxBytes = 2048, clock = { now })
            repeat(100) { index ->
                now++
                store.append(
                    LogEntry(now, LogLevel.INFO, "Download", "event=$index ${"x".repeat(80)}")
                )
            }

            assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 2048)
            val entries = LogFileStore(directory, maxBytes = 2048, clock = { now }).recent()
            assertTrue(entries.first().message.startsWith("event=99 "))
            assertTrue(entries.size < 100)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `expiry removes old entries even when recent entries share the same file`() {
        val directory = Files.createTempDirectory("findroid-expiry").toFile()
        try {
            var now = 1_000_000L
            val store = LogFileStore(directory, retentionMillis = 1000, clock = { now })
            store.append(LogEntry(now, LogLevel.INFO, "App", "expired"))
            now += 1001
            store.append(LogEntry(now, LogLevel.INFO, "App", "current"))

            assertEquals(listOf("current"), store.recent().map { it.message })
            now += 1001
            store.prune(force = true)
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }
}
