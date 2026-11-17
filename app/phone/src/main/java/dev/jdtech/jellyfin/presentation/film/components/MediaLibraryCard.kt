package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.jdtech.jellyfin.core.presentation.utils.containerTransform
import dev.jdtech.jellyfin.core.presentation.utils.containerTransformOnClick
import dev.jdtech.jellyfin.core.presentation.utils.rememberContainerTransformKey
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun MediaLibraryCard(item: FindroidItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transformKey = rememberContainerTransformKey(item.id)
    Surface(
        onClick = containerTransformOnClick(transformKey, onClick),
        modifier = modifier.fillMaxWidth().containerTransform(transformKey),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            ItemPoster(
                item = item,
                direction = Direction.HORIZONTAL,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = item.name,
                modifier = Modifier.padding(MaterialTheme.spacings.medium),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
