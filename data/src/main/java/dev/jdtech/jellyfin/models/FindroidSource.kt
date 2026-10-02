package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import java.util.UUID
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo

data class FindroidSource(
    val id: String,
    val name: String,
    val type: FindroidSourceType,
    val path: String,
    val size: Long,
    val mediaStreams: List<FindroidMediaStream>,
    val downloadId: Long? = null,
)

fun FindroidSource.isPlayableLocalFile(): Boolean {
    return type == FindroidSourceType.LOCAL && !path.endsWith(".download") && File(path).isFile
}

suspend fun MediaSourceInfo.toFindroidSource(
    jellyfinRepository: JellyfinRepository,
    itemId: UUID,
    includePath: Boolean = false,
    forceTranscoding: Boolean = false,
    maxStreamingBitrate: Int? = null,
): FindroidSource {
    val path =
        if (forceTranscoding) {
            transcodingUrl
                ?.let { url ->
                    if (url.startsWith("http")) url
                    else jellyfinRepository.getBaseUrl().trimEnd('/') + url
                }
                .orEmpty()
                .ifEmpty {
                    if (!canFallBackToDirectSource(maxStreamingBitrate)) {
                        ""
                    } else {
                        when (protocol) {
                            MediaProtocol.FILE -> {
                                try {
                                    jellyfinRepository.getStreamUrl(itemId, id.orEmpty())
                                } catch (_: Exception) {
                                    ""
                                }
                            }
                            MediaProtocol.HTTP -> this.path.orEmpty()
                            else -> ""
                        }
                    }
                }
        } else {
            when (protocol) {
                MediaProtocol.FILE -> {
                    try {
                        if (includePath) jellyfinRepository.getStreamUrl(itemId, id.orEmpty())
                        else ""
                    } catch (_: Exception) {
                        ""
                    }
                }
                MediaProtocol.HTTP -> this.path.orEmpty()
                else -> ""
            }
        }
    return FindroidSource(
        id = id.orEmpty(),
        name = name.orEmpty(),
        type = FindroidSourceType.REMOTE,
        path = path,
        size = size ?: 0,
        mediaStreams =
            mediaStreams?.map { it.toFindroidMediaStream(jellyfinRepository) } ?: emptyList(),
    )
}

internal fun MediaSourceInfo.canFallBackToDirectSource(maxStreamingBitrate: Int?): Boolean {
    if (maxStreamingBitrate == null) return true
    if (maxStreamingBitrate <= 0) return false

    // MediaSourceInfo.bitrate is the aggregate source bitrate, including audio.
    return bitrate?.let { it > 0 && it <= maxStreamingBitrate } == true
}

suspend fun FindroidSourceDto.toFindroidSource(
    serverDatabaseDao: ServerDatabaseDao
): FindroidSource {
    return FindroidSource(
        id = id,
        name = name,
        type = type,
        path = path,
        size = File(path).length(),
        mediaStreams =
            serverDatabaseDao.getMediaStreamsBySourceId(id).map { it.toFindroidMediaStream() },
        downloadId = downloadId,
    )
}

enum class FindroidSourceType {
    REMOTE,
    LOCAL,
}
