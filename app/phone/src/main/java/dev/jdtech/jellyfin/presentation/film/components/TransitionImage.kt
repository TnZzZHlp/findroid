package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.presentation.utils.ContainerTransformImage
import dev.jdtech.jellyfin.core.presentation.utils.currentContainerTransformImage
import dev.jdtech.jellyfin.core.presentation.utils.isContainerTransformRunning
import dev.jdtech.jellyfin.core.presentation.utils.sharedTransformImage
import dev.jdtech.jellyfin.presentation.components.RetainedTransitionImage

@Composable
fun SourceTransitionImage(
    model: Any?,
    identity: Any,
    transformKey: Any,
    onImageReady: (ContainerTransformImage?) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .sharedTransformImage(identity, sourceKey = transformKey, shape = shape)
                .clip(shape),
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
        onLoading = { onImageReady(null) },
        onError = { onImageReady(null) },
        onSuccess = {
            onImageReady(ContainerTransformImage(identity, model?.toString(), it.painter, shape))
        },
    )
}

@Composable
fun DetailTransitionImage(identity: Any, model: Any?, modifier: Modifier = Modifier) {
    val source = currentContainerTransformImage()?.takeIf { it.identity == identity }
    var loaded by remember(model) { mutableStateOf(false) }
    val running = isContainerTransformRunning()
    val targetAlpha by
        animateFloatAsState(
            targetValue = if (source == null || (loaded && !running)) 1f else 0f,
            label = "detail_image_reveal",
        )
    Box(modifier = modifier.sharedTransformImage(identity)) {
        source?.let { RetainedTransitionImage(it) }
        if (source?.model == model?.toString() && source != null) return@Box
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            placeholder =
                if (source == null) ColorPainter(MaterialTheme.colorScheme.surfaceContainer)
                else null,
            alpha = targetAlpha,
            onSuccess = { loaded = true },
        )
    }
}
