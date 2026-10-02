package dev.jdtech.jellyfin.settings.domain

import java.util.Locale
import java.util.UUID

data class SubtitleSelectionPreference(
    val title: String?,
    val language: String?,
    val disabled: Boolean,
)

data class SubtitleTrackIdentity(
    val title: String?,
    val language: String?,
)

data class SubtitleSelectionScope(val id: UUID, val isSeries: Boolean)

fun subtitleSelectionPreferenceKey(
    serverId: String,
    userId: UUID,
    scope: SubtitleSelectionScope,
): String {
    val scopeType = if (scope.isSeries) "series" else "item"
    return "pref_player_subtitle_selection_${serverId}_${userId}_${scopeType}_${scope.id}"
}

fun findMatchingSubtitleTrack(
    preference: SubtitleSelectionPreference,
    tracks: List<SubtitleTrackIdentity>,
): Int? {
    if (preference.disabled) return null

    findMatchingSubtitleTrackByTitle(preference, tracks)?.let {
        return it
    }

    val language = preference.language.normalizeSubtitleLanguage()
    if (language != null) {
        tracks
            .indexOfFirst { track -> track.language.normalizeSubtitleLanguage() == language }
            .takeIf { it >= 0 }
            ?.let {
                return it
            }
    }

    return null
}

fun findMatchingSubtitleTrackByTitle(
    preference: SubtitleSelectionPreference,
    tracks: List<SubtitleTrackIdentity>,
): Int? {
    val title = preference.title.normalizeSubtitleTitle() ?: return null
    val language = preference.language.normalizeSubtitleLanguage()
    return tracks
        .indexOfFirst { track ->
            track.title.normalizeSubtitleTitle() == title &&
                languagesAreCompatible(language, track.language.normalizeSubtitleLanguage())
        }
        .takeIf { it >= 0 }
}

private fun String?.normalizeSubtitleTitle(): String? {
    return this?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)
}

private fun String?.normalizeSubtitleLanguage(): String? {
    return this?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("und", ignoreCase = true) }
        ?.lowercase(Locale.ROOT)
        ?.substringBefore('-')
        ?.substringBefore('_')
}

private fun languagesAreCompatible(preference: String?, track: String?): Boolean {
    return preference == null || track == null || preference == track
}
