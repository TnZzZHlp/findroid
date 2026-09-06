package dev.jdtech.jellyfin.film.presentation.home

import java.util.UUID

/** Membership results live for one home refresh, never across servers or users. */
internal class HomeFilter(
    private val hiddenLibraryIds: Set<UUID>,
    private val ancestorIds: suspend (UUID) -> Set<UUID>,
) {
    private val hiddenItems = mutableMapOf<UUID, Boolean>()

    suspend fun <T> filter(items: List<T>, id: (T) -> UUID): List<T> {
        if (hiddenLibraryIds.isEmpty()) return items
        return items.filterNot { item ->
            val itemId = id(item)
            hiddenItems.getOrPut(itemId) {
                itemId in hiddenLibraryIds || ancestorIds(itemId).any { it in hiddenLibraryIds }
            }
        }
    }
}
