package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.utils.containerTransform
import dev.jdtech.jellyfin.core.presentation.utils.containerTransformOnClick
import dev.jdtech.jellyfin.core.presentation.utils.rememberContainerTransformKey
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun DownloadsCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transformKey = rememberContainerTransformKey("downloads")
    OutlinedCard(
        onClick = containerTransformOnClick(transformKey, onClick),
        modifier = modifier.containerTransform(transformKey),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacings.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painter = painterResource(CoreR.drawable.ic_download), contentDescription = null)
            Text(text = stringResource(CoreR.string.title_downloads))
        }
    }
}
