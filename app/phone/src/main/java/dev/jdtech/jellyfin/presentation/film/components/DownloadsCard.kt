package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.utils.containerTransform
import dev.jdtech.jellyfin.core.presentation.utils.containerTransformOnClick
import dev.jdtech.jellyfin.core.presentation.utils.rememberContainerTransformKey

@Composable
fun DownloadsCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transformKey = rememberContainerTransformKey("downloads")
    LibraryShortcutCard(
        title = stringResource(CoreR.string.title_downloads),
        icon = CoreR.drawable.ic_download,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        onClick = containerTransformOnClick(transformKey, onClick),
        modifier = modifier.containerTransform(transformKey),
    )
}
