package dev.jdtech.jellyfin.core.presentation.utils

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable fun rememberContainerTransformKey(value: Any): Any = value

@Composable fun ContainerTransformHost(content: @Composable () -> Unit) = content()

@Composable
fun AnimatedVisibilityScope.ContainerTransformScreen(
    consumePendingTransform: Boolean = false,
    content: @Composable () -> Unit,
) = content()

@Composable fun Modifier.containerTransform(key: Any): Modifier = this

@Composable
fun containerTransformOnClick(key: Any, onClick: () -> Unit): () -> Unit = onClick
