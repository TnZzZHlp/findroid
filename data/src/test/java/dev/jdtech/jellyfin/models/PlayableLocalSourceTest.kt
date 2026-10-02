package dev.jdtech.jellyfin.models

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlayableLocalSourceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `completed local files are playable`() {
        val file = temporaryFolder.newFile("episode.mkv").apply { writeText("video") }
        assertTrue(source(file).isPlayableLocalFile())
    }

    @Test
    fun `incomplete downloads are excluded even when the file exists`() {
        val file = temporaryFolder.newFile("episode.mkv.download").apply { writeText("partial") }
        assertFalse(source(file).isPlayableLocalFile())
    }

    @Test
    fun `missing files and directories are excluded`() {
        assertFalse(source(File(temporaryFolder.root, "missing.mkv")).isPlayableLocalFile())
        assertFalse(source(temporaryFolder.newFolder("directory.mkv")).isPlayableLocalFile())
    }

    @Test
    fun `remote sources are excluded even when their path is a local file`() {
        val file = temporaryFolder.newFile("episode.mkv").apply { writeText("video") }
        assertFalse(source(file).copy(type = FindroidSourceType.REMOTE).isPlayableLocalFile())
    }

    private fun source(file: File) =
        FindroidSource(
            id = "local-source",
            name = "Episode",
            type = FindroidSourceType.LOCAL,
            path = file.path,
            size = file.length(),
            mediaStreams = emptyList(),
        )
}
