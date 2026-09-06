package dev.jdtech.jellyfin.settings.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}
