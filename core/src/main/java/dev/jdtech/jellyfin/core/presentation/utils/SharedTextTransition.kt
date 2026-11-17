package dev.jdtech.jellyfin.core.presentation.utils

import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class ContainerTransformText(val identity: Any, val text: String)

private data class SharedTextKey(val containerKey: Any, val identity: Any)

@Composable
fun currentContainerTransformText(): ContainerTransformText? =
    LocalContainerTransformTarget.current?.text

@Composable
fun Modifier.sharedTransformText(identity: Any, sourceKey: Any? = null): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val target = LocalContainerTransformTarget.current
    val containerKey = sourceKey ?: target?.takeIf { it.text?.identity == identity }?.key

    return if (
        sharedTransitionScope != null && animatedVisibilityScope != null && containerKey != null
    ) {
        with(sharedTransitionScope) {
            sharedBounds(
                sharedContentState =
                    rememberSharedContentState(SharedTextKey(containerKey, identity)),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = RemeasureToBounds,
            )
        }
    } else {
        this
    }
}
