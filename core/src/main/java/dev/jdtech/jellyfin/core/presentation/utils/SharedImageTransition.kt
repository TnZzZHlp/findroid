package dev.jdtech.jellyfin.core.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter

data class ContainerTransformImage(
    val identity: Any,
    val model: String?,
    val painter: Painter,
    val shape: Shape = RectangleShape,
)

private data class SharedImageKey(val containerKey: Any, val identity: Any)

@Composable
fun currentContainerTransformImage(): ContainerTransformImage? =
    LocalContainerTransformTarget.current?.image

@Composable
fun isContainerTransformRunning(): Boolean =
    LocalSharedTransitionScope.current?.isTransitionActive == true

@Composable
fun Modifier.sharedTransformImage(
    identity: Any,
    sourceKey: Any? = null,
    shape: Shape? = null,
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val target = LocalContainerTransformTarget.current
    val containerKey = sourceKey ?: target?.takeIf { it.image?.identity == identity }?.key

    return if (
        sharedTransitionScope != null && animatedVisibilityScope != null && containerKey != null
    ) {
        with(sharedTransitionScope) {
            sharedElement(
                sharedContentState =
                    rememberSharedContentState(SharedImageKey(containerKey, identity)),
                animatedVisibilityScope = animatedVisibilityScope,
                clipInOverlayDuringTransition =
                    OverlayClip(shape ?: target?.image?.shape ?: RectangleShape),
            )
        }
    } else {
        this
    }
}
