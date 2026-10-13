package dev.jdtech.jellyfin.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.tonyodev.fetch2.HttpUrlConnectionDownloader
import com.tonyodev.fetch2core.Downloader
import com.tonyodev.fetch2core.InterruptMonitor
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.IOException
import java.net.HttpURLConnection

class ResumableHttpDownloader(
    context: Context,
    private val preferences: AppPreferences,
    private val tokenProvider: (Downloader.ServerRequest) -> String? = { null },
) :
    HttpUrlConnectionDownloader(
        HttpUrlConnectionPreferences().apply { followsRedirect = false },
        Downloader.FileDownloaderType.PARALLEL,
    ) {
    private val metadata = context.getSharedPreferences("download-http", Context.MODE_PRIVATE)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val validatorLock = Any()

    override fun onPreClientExecute(
        client: HttpURLConnection,
        request: Downloader.ServerRequest,
    ): Void? {
        val origin = java.net.URL(request.url)
        if (
            client.url.host != origin.host ||
                client.url.protocol != origin.protocol ||
                client.url.port != origin.port
        ) {
            throw DownloadHttpException(302)
        }
        super.onPreClientExecute(client, request)
        client.setRequestProperty("Accept-Encoding", "identity")
        tokenProvider(request)?.let { client.setRequestProperty("X-Emby-Token", it) }
        metadata.getString("${request.id}:validator", null)?.let {
            client.setRequestProperty("If-Range", it)
        }
        return null
    }

    override fun execute(
        request: Downloader.ServerRequest,
        interruptMonitor: InterruptMonitor,
    ): Downloader.Response? {
        val network = connectivity.activeNetwork
        val capabilities = connectivity.getNetworkCapabilities(network)
        if (
            capabilities == null ||
                (!preferences.getValue(preferences.downloadOverMobileData) &&
                    connectivity.isActiveNetworkMetered) ||
                (!preferences.getValue(preferences.downloadWhenRoaming) &&
                    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING))
        ) {
            throw IOException("Waiting for an allowed network")
        }
        val response =
            try {
                super.execute(request, interruptMonitor)
            } catch (e: DownloadHttpException) {
                metadata.edit().putString("${request.id}:problem", "http:${e.statusCode}").commit()
                throw e
            } ?: return null
        try {
            if (!response.isSuccessful) {
                if (response.code == 416) throw RestartDownloadException()
                throw DownloadHttpException(response.code)
            }
            if (
                response.header("Content-Encoding")?.lowercase()?.let { it != "identity" } == true
            ) {
                throw RestartDownloadException()
            }
            if (
                response.header("Content-Type")?.substringBefore(";")?.trim()?.lowercase() in
                    setOf(
                        "text/html",
                        "application/xhtml+xml",
                        "application/json",
                        "application/problem+json",
                    )
            ) {
                throw DownloadContentException()
            }
            val etag = response.header("ETag")?.takeUnless { it.startsWith("W/") }
            val validator = etag ?: response.header("Last-Modified")
            val range = request.headers.entries.firstOrNull { it.key.equals("Range", true) }?.value
            val start = range?.substringAfter("bytes=")?.substringBefore("-")?.toLongOrNull() ?: 0
            synchronized(validatorLock) {
                val previousTotal = metadata.getLong("${request.id}:total", -1)
                if (
                    previousTotal > 0 &&
                        response.contentLength > 0 &&
                        previousTotal != response.contentLength
                ) {
                    throw RestartDownloadException(false)
                }
                if (response.contentLength > 0)
                    metadata.edit().putLong("${request.id}:total", response.contentLength).commit()
                val previous = metadata.getString("${request.id}:validator", null)
                if ((start > 0 || request.segment > 1) && previous == null) {
                    throw UnvalidatedResumeException()
                }
                if (
                    (previous != null && previous != validator) ||
                        (previous == null && validator != null && previousTotal > 0)
                ) {
                    throw RestartDownloadException(false)
                }
                if (validator != null && previous == null) {
                    metadata.edit().putString("${request.id}:validator", validator).commit()
                }
            }
            val end = range?.substringAfter("-")?.toLongOrNull()
            if (response.code == 206) {
                val match =
                    CONTENT_RANGE.matchEntire(response.header("Content-Range").orEmpty())
                        ?: throw RestartDownloadException()
                val actualStart =
                    match.groupValues[1].toLongOrNull() ?: throw RestartDownloadException()
                val actualEnd =
                    match.groupValues[2].toLongOrNull() ?: throw RestartDownloadException()
                val total = match.groupValues[3].toLongOrNull() ?: throw RestartDownloadException()
                if (
                    actualStart != start ||
                        actualEnd < actualStart ||
                        actualEnd >= total ||
                        (end != null && actualEnd != minOf(end, total - 1))
                ) {
                    throw RestartDownloadException()
                }
            } else if (start > 0 || (request.segment > 0 && end != null)) {
                throw RestartDownloadException()
            }
            return Downloader.Response(
                    response.code,
                    response.isSuccessful,
                    response.contentLength,
                    response.byteStream,
                    response.request,
                    response.hash,
                    response.responseHeaders,
                    response.code == 206,
                    null,
                )
                .also { responses[it] = response }
        } catch (e: Exception) {
            val problem =
                when (e) {
                    is RestartDownloadException -> if (e.sequential) "reset" else "entity"
                    is UnvalidatedResumeException -> "unvalidated"
                    is DownloadHttpException -> "http:${e.statusCode}"
                    is DownloadContentException -> "content"
                    else -> "network"
                }
            metadata.edit().putString("${request.id}:problem", problem).commit()
            super.disconnect(response)
            throw e
        }
    }

    private val responses =
        java.util.concurrent.ConcurrentHashMap<Downloader.Response, Downloader.Response>()

    override fun disconnect(response: Downloader.Response) {
        super.disconnect(responses.remove(response) ?: response)
    }

    override fun getRequestSupportedFileDownloaderTypes(
        request: Downloader.ServerRequest
    ): Set<Downloader.FileDownloaderType> {
        if (request.extras.getBoolean("sequential", false))
            return setOf(Downloader.FileDownloaderType.SEQUENTIAL)
        val probe =
            Downloader.ServerRequest(
                request.id,
                request.url,
                request.headers + ("Range" to "bytes=0-0"),
                request.file,
                request.fileUri,
                request.tag,
                request.identifier,
                "GET",
                request.extras,
                request.redirected,
                request.redirectUrl,
                0,
            )
        val response =
            try {
                execute(
                    probe,
                    object : InterruptMonitor {
                        override val isInterrupted = false
                    },
                )
            } catch (e: Exception) {
                return setOf(Downloader.FileDownloaderType.SEQUENTIAL)
            }
        return try {
            if (
                response?.acceptsRanges == true &&
                    hasValidator(response) &&
                    response.contentLength >= 4 * 1024 * 1024
            ) {
                setOf(
                    Downloader.FileDownloaderType.SEQUENTIAL,
                    Downloader.FileDownloaderType.PARALLEL,
                )
            } else setOf(Downloader.FileDownloaderType.SEQUENTIAL)
        } finally {
            response?.let { disconnect(it) }
        }
    }

    override fun getRequestFileDownloaderType(
        request: Downloader.ServerRequest,
        supportedFileDownloaderTypes: Set<Downloader.FileDownloaderType>,
    ): Downloader.FileDownloaderType =
        if (Downloader.FileDownloaderType.PARALLEL in supportedFileDownloaderTypes)
            Downloader.FileDownloaderType.PARALLEL
        else Downloader.FileDownloaderType.SEQUENTIAL

    override fun getFileSlicingCount(request: Downloader.ServerRequest, contentLength: Long): Int =
        4

    private fun hasValidator(response: Downloader.Response): Boolean =
        response.header("ETag")?.startsWith("W/") == false ||
            response.header("Last-Modified") != null

    fun clear(id: Int) {
        metadata.edit().remove("$id:validator").remove("$id:total").remove("$id:problem").commit()
    }

    fun problem(id: Int): String = metadata.getString("$id:problem", "").orEmpty()

    fun clearProblem(id: Int) {
        metadata.edit().remove("$id:problem").commit()
    }

    private fun Downloader.Response.header(name: String): String? =
        responseHeaders.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()

    companion object {
        private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
    }
}

class RestartDownloadException(val sequential: Boolean = true) :
    IOException("The remote file changed or did not honor the requested range")

class UnvalidatedResumeException : IOException("Cannot resume without a stable HTTP validator")

class DownloadContentException : IOException("The server did not return media content")

class DownloadHttpException(val statusCode: Int) : IOException("HTTP $statusCode")
