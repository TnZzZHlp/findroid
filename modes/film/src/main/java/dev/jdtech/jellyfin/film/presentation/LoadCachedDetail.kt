package dev.jdtech.jellyfin.film.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/** Show durable download data before attempting a bounded network refresh. */
internal suspend fun <T : Any> loadCachedDetail(
    readCache: suspend () -> T?,
    fetchRemote: suspend (hasCachedData: Boolean) -> T,
    onLoaded: suspend (T) -> Unit,
    onError: suspend (Exception) -> Unit,
    refreshTimeoutMillis: Long = 10_000L,
) {
    val cached =
        try {
            readCache()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            currentCoroutineContext().ensureActive()
            null
        }
    if (cached != null) onLoaded(cached)

    try {
        val refreshed = withTimeout(refreshTimeoutMillis) { fetchRemote(cached != null) }
        onLoaded(refreshed)
    } catch (error: Exception) {
        if (error is CancellationException && error !is TimeoutCancellationException) throw error
        currentCoroutineContext().ensureActive()
        // A failed refresh must not replace usable local content with a loading/error screen.
        if (cached == null) onError(error)
    }
}
