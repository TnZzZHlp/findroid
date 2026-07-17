package dev.jdtech.jellyfin.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sources")
data class FindroidSourceDto(
    @PrimaryKey val id: String,
    val itemId: UUID,
    val name: String,
    val type: FindroidSourceType,
    val path: String,
    val downloadId: Long? = null,
)

fun FindroidSource.toFindroidSourceDto(
    itemId: UUID,
    path: String,
    localSourceId: String,
): FindroidSourceDto {
    return FindroidSourceDto(
        id = localSourceId,
        itemId = itemId,
        name = name,
        type = FindroidSourceType.LOCAL,
        path = path,
    )
}

fun localSourceId(itemId: UUID, remoteSourceId: String): String {
    return "local-$itemId-$remoteSourceId"
}
