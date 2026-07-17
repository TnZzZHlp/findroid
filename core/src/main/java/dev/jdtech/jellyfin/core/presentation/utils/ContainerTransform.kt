package dev.jdtech.jellyfin.core.presentation.utils

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

private data class ContainerTransformKey(val value: Any)

private class ContainerTransformState {
    private var pendingKey: Any? = null

    fun prepare(key: Any) {
        pendingKey = key
    }

    fun consume(): Any? = pendingKey.also { pendingKey = null }
}

private val LocalContainerTransformState =
    staticCompositionLocalOf<ContainerTransformState?> { null }

private val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

private val LocalAnimatedVisibilityScope =
    staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun rememberContainerTransformKey(value: Any): Any =
    remember(value) { ContainerTransformKey(value) }

@Composable
fun ContainerTransformHost(content: @Composable () -> Unit) {
    val state = remember { ContainerTransformState() }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalContainerTransformState provides state,
            LocalSharedTransitionScope provides this,
            content = content,
        )
    }
}

@Composable
fun AnimatedVisibilityScope.ContainerTransformScreen(
    consumePendingTransform: Boolean = false,
    content: @Composable () -> Unit,
) {
    val state = LocalContainerTransformState.current
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val transformKey =
        remember(state, consumePendingTransform) {
            if (consumePendingTransform) state?.consume() else null
        }
    val transformModifier =
        if (sharedTransitionScope != null && transformKey != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(transformKey),
                    animatedVisibilityScope = this@ContainerTransformScreen,
                    resizeMode = RemeasureToBounds,
                )
            }
        } else {
            Modifier
        }

    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
        Box(modifier = Modifier.fillMaxSize().then(transformModifier)) { content() }
    }
}

@Composable
fun Modifier.containerTransform(key: Any): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    return if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = RemeasureToBounds,
            )
        }
    } else {
        this
    }
}

@Composable
fun containerTransformOnClick(key: Any, onClick: () -> Unit): () -> Unit {
    val state = LocalContainerTransformState.current
    val currentOnClick = rememberUpdatedState(onClick)

    return remember(key, state) {
        {
            state?.prepare(key)
            currentOnClick.value()
        }
    }
}
