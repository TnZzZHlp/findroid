package dev.jdtech.jellyfin.downloads

import com.tonyodev.fetch2core.Downloader
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Singleton
class DownloadAuthentication
@Inject
constructor(
    private val database: ServerDatabaseDao,
    private val preferences: AppPreferences,
) {
    fun token(request: Downloader.ServerRequest): String =
        runBlocking(Dispatchers.IO) {
            val serverId = request.extras.getString("serverId", "")
            if (preferences.getValue(preferences.currentServer) != serverId)
                throw DownloadHttpException(401)
            val server = database.getServerWithAddresses(serverId)
            val user = server.user
            val requested = URL(request.url)
            if (
                !server.addresses.any { address ->
                    val origin = runCatching { URL(address.address) }.getOrNull()
                    origin != null &&
                        origin.protocol == requested.protocol &&
                        origin.host.equals(requested.host, ignoreCase = true) &&
                        (origin.port.takeIf { it >= 0 } ?: origin.defaultPort) ==
                            (requested.port.takeIf { it >= 0 } ?: requested.defaultPort)
                }
            )
                throw DownloadHttpException(401)
            if (user?.id?.toString() != request.extras.getString("userId", ""))
                throw DownloadHttpException(401)
            user.accessToken?.takeIf { it.isNotBlank() } ?: throw DownloadHttpException(401)
        }
}
