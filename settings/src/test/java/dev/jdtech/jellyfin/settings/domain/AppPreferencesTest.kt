package dev.jdtech.jellyfin.settings.domain

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun `hidden libraries use a separate key for each server`() {
        assertEquals(
            "home_hidden_libraries_server-a",
            homeHiddenLibrariesPreferenceKey("server-a"),
        )
        assertNotEquals(
            homeHiddenLibrariesPreferenceKey("server-a"),
            homeHiddenLibrariesPreferenceKey("server-b"),
        )
    }

    @Test
    fun `subtitle selections are isolated by server user and scope`() {
        val scopeId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val userA = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val userB = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val seriesScope = SubtitleSelectionScope(scopeId, isSeries = true)
        val itemScope = SubtitleSelectionScope(scopeId, isSeries = false)
        val key = subtitleSelectionPreferenceKey("server-a", userA, seriesScope)

        assertNotEquals(key, subtitleSelectionPreferenceKey("server-a", userB, seriesScope))
        assertNotEquals(key, subtitleSelectionPreferenceKey("server-b", userA, seriesScope))
        assertNotEquals(key, subtitleSelectionPreferenceKey("server-a", userA, itemScope))
    }

    @Test
    fun `subtitle matching prefers compatible title then language`() {
        val preference =
            SubtitleSelectionPreference(title = "English", language = "en-US", disabled = false)
        val tracks =
            listOf(
                SubtitleTrackIdentity(title = "English", language = "fr"),
                SubtitleTrackIdentity(title = "Signs", language = "en"),
                SubtitleTrackIdentity(title = "English", language = "en"),
            )

        assertEquals(2, findMatchingSubtitleTrack(preference, tracks))
        assertEquals(
            1,
            findMatchingSubtitleTrack(
                preference.copy(title = "Missing"),
                tracks,
            ),
        )
    }

    @Test
    fun `subtitle matching leaves automatic selection for off and unmatched preferences`() {
        val tracks = listOf(SubtitleTrackIdentity(title = "English", language = "en"))

        assertNull(
            findMatchingSubtitleTrack(
                SubtitleSelectionPreference(title = null, language = null, disabled = true),
                tracks,
            )
        )
        assertNull(
            findMatchingSubtitleTrack(
                SubtitleSelectionPreference(title = "Missing", language = "ja", disabled = false),
                tracks,
            )
        )
    }
}
