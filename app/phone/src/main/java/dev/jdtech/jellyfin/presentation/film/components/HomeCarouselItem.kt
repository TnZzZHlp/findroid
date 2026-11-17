package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.core.presentation.utils.ContainerTransformImage
import dev.jdtech.jellyfin.core.presentation.utils.ContainerTransformText
import dev.jdtech.jellyfin.core.presentation.utils.containerTransform
import dev.jdtech.jellyfin.core.presentation.utils.containerTransformOnClick
import dev.jdtech.jellyfin.core.presentation.utils.rememberContainerTransformKey
import dev.jdtech.jellyfin.core.presentation.utils.sharedTransformText
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun HomeCarouselItem(item: FindroidItem, onAction: (HomeAction) -> Unit) {
    val transformKey = rememberContainerTransformKey("home_carousel" to item.id)
    var transitionImage by remember(item.id) { mutableStateOf<ContainerTransformImage?>(null) }
    val transformedOnClick =
        containerTransformOnClick(
            transformKey,
            transitionImage,
            ContainerTransformText(item.id, item.name),
        ) {
            onAction(HomeAction.OnItemClick(item))
        }
    val colorStops =
        arrayOf(
            0.0f to Color.Black.copy(alpha = 0.1f),
            0.5f to Color.Black.copy(alpha = 0.35f),
            1f to Color.Black.copy(alpha = 0.85f),
        )

    Box(
        modifier =
            Modifier.aspectRatio(1.77f)
                .containerTransform(transformKey)
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(
                    onClickLabel = stringResource(CoreR.string.view_details),
                    onClick = transformedOnClick,
                )
    ) {
        SourceTransitionImage(
            model = item.images.backdrop,
            shape = MaterialTheme.shapes.extraLarge,
            identity = item.id,
            transformKey = transformKey,
            onImageReady = { transitionImage = it },
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.verticalGradient(colorStops = colorStops))
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            modifier =
                Modifier.padding(
                        horizontal = MaterialTheme.spacings.default,
                        vertical = MaterialTheme.spacings.medium,
                    )
                    .align(Alignment.BottomStart),
        ) {
            val genres =
                when (item) {
                    is FindroidMovie -> item.genres
                    is FindroidShow -> item.genres
                    else -> emptyList()
                }
            Text(
                text = genres.joinToString(),
                color = Color.LightGray,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = item.name,
                modifier = Modifier.sharedTransformText(item.id, sourceKey = transformKey),
                color = Color.White,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun HomeCarouselItemPreview() {
    FindroidTheme { HomeCarouselItem(item = dummyMovie, onAction = {}) }
}
