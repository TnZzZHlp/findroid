package dev.jdtech.jellyfin.settings.domain

import android.content.SharedPreferences
import dev.jdtech.jellyfin.settings.domain.models.Preference
import javax.inject.Inject
import timber.log.Timber

internal fun homeHiddenLibrariesPreferenceKey(serverId: String?): String {
    return "home_hidden_libraries_${serverId.orEmpty()}"
}

class AppPreferences @Inject constructor(val sharedPreferences: SharedPreferences) {
    // Server
    val currentServer = Preference<String?>("pref_current_server", null)

    // Language
    val preferredAudioLanguage = Preference<String?>("pref_audio_language", null)
    val preferredSubtitleLanguage = Preference<String?>("pref_subtitle_language", null)

    // Interface
    val theme = Preference("pref_theme", "system")
    val dynamicColors = Preference("pref_dynamic_colors", true)
    val homeSuggestions = Preference<Boolean>("home_suggestions", true)
    val homeContinueWatching = Preference<Boolean>("home_continue_watching", true)
    val homeNextUp = Preference<Boolean>("home_next_up", true)
    val homeLatest = Preference<Boolean>("home_latest", true)
    val homeHiddenLibraries = Preference<Set<String>>("home_hidden_libraries", emptySet())
    val displayExtraInfo = Preference("pref_display_extra_info", false)

    // Player
    val playerBrightness = Preference("pref_player_brightness", -1.0f)

    // Player - mpv
    val playerMpvHwdec = Preference("pref_player_mpv_hwdec", "mediacodec")
    val playerMpvVo = Preference("pref_player_mpv_vo", "gpu-next")
    val playerMpvAo = Preference("pref_player_mpv_ao", "aaudio")

    // Player - gestures
    val playerGestures = Preference("pref_player_gestures", true)
    val playerGesturesVB = Preference("pref_player_gestures_vb", true)
    val playerGesturesZoom = Preference("pref_player_gestures_zoom", true)
    val playerGesturesSeek = Preference("pref_player_gestures_seek", true)
    val playerGesturesSeekTrickplay = Preference("pref_player_gestures_seek_trickplay", true)
    val playerGesturesChapterSkip = Preference("pref_player_gestures_chapter_skip", true)
    val playerGesturesBrightnessRemember = Preference("pref_player_brightness_remember", false)
    val playerGesturesStartMaximized = Preference("pref_player_start_maximized", false)

    // Player - seeking
    val playerSeekBackInc = Preference("pref_player_seek_back_inc", 5_000L)
    val playerSeekForwardInc = Preference("pref_player_seek_forward_inc", 15_000L)
    val playerChapterMarkers = Preference("pref_player_chapter_markers", true)

    // Player - Media Segments
    val playerMediaSegmentsSkipButton
        get() = Preference("pref_player_media_segments_skip_button", true)

    val playerMediaSegmentsSkipButtonType
        get() = Preference("pref_player_media_segments_skip_button_type", setOf("INTRO", "OUTRO"))

    val playerMediaSegmentsSkipButtonDuration
        get() = Preference("pref_player_media_segments_skip_button_duration", 5L)

    val playerMediaSegmentsAutoSkip
        get() = Preference("pref_player_media_segments_auto_skip", false)

    val playerMediaSegmentsAutoSkipMode
        get() =
            Preference(
                "pref_player_media_segments_auto_skip_mode",
                Constants.PlayerMediaSegmentsAutoSkip.ALWAYS,
            )

    val playerMediaSegmentsAutoSkipType
        get() = Preference("pref_player_media_segments_auto_skip_type", setOf("INTRO", "OUTRO"))

    val playerMediaSegmentsNextEpisodeThreshold
        get() = Preference("pref_player_media_segments_next_episode_threshold", 5_000L)

    // Player - trickplay
    val playerTrickplay = Preference("pref_player_trickplay", true)

    // Player - PiP
    val playerPipGesture = Preference("pref_player_picture_in_picture_gesture", false)

    // Downloads
    val downloadOverMobileData = Preference("pref_downloads_mobile_data", false)
    val downloadWhenRoaming = Preference("pref_downloads_roaming", false)

    // Network
    val requestTimeout =
        Preference("pref_network_request_timeout", Constants.NETWORK_DEFAULT_REQUEST_TIMEOUT)
    val connectTimeout =
        Preference("pref_network_connect_timeout", Constants.NETWORK_DEFAULT_CONNECT_TIMEOUT)
    val socketTimeout =
        Preference("pref_network_socket_timeout", Constants.NETWORK_DEFAULT_SOCKET_TIMEOUT)

    // Cache
    val imageCache = Preference("pref_image_cache", true)
    val imageCacheSize = Preference("pref_image_cache_size", 20)

    // Sorting
    val sortBy = Preference("pref_sort_by", "SortName")
    val sortOrder = Preference("pref_sort_order", "Ascending")

    // Migrations
    val mpvMigrated = Preference("mpv_migrated", false)

    @PublishedApi
    internal fun storageKey(preference: Preference<*>): String {
        return if (preference.backendName == homeHiddenLibraries.backendName) {
            homeHiddenLibrariesPreferenceKey(getValue(currentServer))
        } else {
            preference.backendName
        }
    }

    inline fun <reified T> getValue(preference: Preference<T>): T {
        val storageKey = storageKey(preference)
        return try {
            @Suppress("UNCHECKED_CAST")
            when (preference.defaultValue) {
                is Boolean -> sharedPreferences.getBoolean(storageKey, preference.defaultValue) as T
                is Int -> sharedPreferences.getInt(storageKey, preference.defaultValue) as T
                is Long -> sharedPreferences.getLong(storageKey, preference.defaultValue) as T
                is Float -> sharedPreferences.getFloat(storageKey, preference.defaultValue) as T
                is String? -> sharedPreferences.getString(storageKey, preference.defaultValue) as T
                is Set<*> ->
                    sharedPreferences.getStringSet(
                        storageKey,
                        preference.defaultValue as Set<String>,
                    ) as T
                else -> preference.defaultValue
            }
        } catch (_: Exception) {
            Timber.w(
                "Failed to load ${preference.backendName} preference. Resetting to default value..."
            )
            setValue(preference, preference.defaultValue)
            preference.defaultValue
        }
    }

    inline fun <reified T> setValue(preference: Preference<T>, value: T) {
        val storageKey = storageKey(preference)
        val editor = sharedPreferences.edit()
        @Suppress("UNCHECKED_CAST")
        when (preference.defaultValue) {
            is Boolean -> editor.putBoolean(storageKey, value as Boolean)
            is Int -> editor.putInt(storageKey, value as Int)
            is Long -> editor.putLong(storageKey, value as Long)
            is Float -> editor.putFloat(storageKey, value as Float)
            is String? -> editor.putString(storageKey, value as String?)
            is Set<*> -> editor.putStringSet(storageKey, value as Set<String>)
            else -> throw Exception()
        }
        editor.apply()
    }
}
