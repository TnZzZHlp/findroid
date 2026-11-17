package dev.jdtech.jellyfin.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import dev.jdtech.jellyfin.core.presentation.utils.ContainerTransformImage

@Composable
fun RetainedTransitionImage(image: ContainerTransformImage, modifier: Modifier = Modifier) {
    val size = image.painter.intrinsicSize
    val ratio = size.width / size.height
    val imageBounds = if (ratio.isFinite() && ratio > 0f) Modifier.aspectRatio(ratio) else Modifier
    Image(
        painter = image.painter,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize().wrapContentSize().then(imageBounds).clip(image.shape),
    )
}
