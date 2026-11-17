package dev.jdtech.jellyfin.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import dev.jdtech.jellyfin.core.presentation.utils.currentContainerTransformImage
import dev.jdtech.jellyfin.core.presentation.utils.currentContainerTransformText
import dev.jdtech.jellyfin.core.presentation.utils.sharedTransformImage
import dev.jdtech.jellyfin.core.presentation.utils.sharedTransformText
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding

@Composable
fun PersonSkeleton(modifier: Modifier = Modifier) {
    val safe = rememberSafePadding()
    val wide =
        currentWindowAdaptiveInfo()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    Surface(
        modifier =
            modifier.padding(
                start = safe.start + 24.dp,
                end = safe.end + 24.dp,
                top = safe.top + 72.dp,
            ),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        if (wide) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                PersonSkeletonImage()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PersonSkeletonName()
                    TextSkeleton()
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PersonSkeletonImage()
                PersonSkeletonName()
                TextSkeleton()
            }
        }
    }
}

@Composable
private fun PersonSkeletonImage() {
    val image = currentContainerTransformImage()
    val modifier = Modifier.height(240.dp).aspectRatio(0.66f).clip(MaterialTheme.shapes.extraLarge)
    if (image != null) {
        RetainedTransitionImage(image, modifier.sharedTransformImage(image.identity))
    } else SkeletonBlock(modifier)
}

@Composable
private fun PersonSkeletonName() {
    val title = currentContainerTransformText()
    if (title != null) {
        Text(
            title.text,
            modifier = Modifier.sharedTransformText(title.identity),
            style = MaterialTheme.typography.headlineMedium,
        )
    } else SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(36.dp))
}
