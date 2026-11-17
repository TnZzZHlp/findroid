package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.utils.containerTransform
import dev.jdtech.jellyfin.core.presentation.utils.containerTransformOnClick
import dev.jdtech.jellyfin.core.presentation.utils.rememberContainerTransformKey
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun FavoritesCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transformKey = rememberContainerTransformKey("favorites")
    LibraryShortcutCard(
        title = stringResource(CoreR.string.title_favorite),
        icon = CoreR.drawable.ic_star,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        onClick = containerTransformOnClick(transformKey, onClick),
        modifier = modifier.containerTransform(transformKey),
    )
}

@Preview
@Composable
private fun FavoritesCardPreview() {
    FindroidTheme { FavoritesCard(onClick = {}, modifier = Modifier.width(320.dp)) }
}
