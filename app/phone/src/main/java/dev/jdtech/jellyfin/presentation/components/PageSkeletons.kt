package dev.jdtech.jellyfin.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.utils.currentContainerTransformImage
import dev.jdtech.jellyfin.core.presentation.utils.currentContainerTransformText
import dev.jdtech.jellyfin.core.presentation.utils.sharedTransformImage
import dev.jdtech.jellyfin.core.presentation.utils.sharedTransformText
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding

@Composable
fun PosterSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(0.66f))
        SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(20.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(16.dp))
    }
}

@Composable
fun PosterGridSkeleton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        userScrollEnabled = false,
    ) {
        items(8) { PosterSkeleton() }
    }
}

@Composable
fun DetailSkeleton(
    modifier: Modifier = Modifier,
    person: Boolean = false,
    season: Boolean = false,
) {
    if (person) {
        PersonSkeleton(modifier)
        return
    }
    val safePadding = rememberSafePadding()
    val sourceImage = currentContainerTransformImage()
    val bodyPadding =
        Modifier.padding(start = safePadding.start + 24.dp, end = safePadding.end + 24.dp)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = if (person) safePadding.start + 16.dp else 0.dp,
                top = if (person) safePadding.top + 80.dp else 0.dp,
                end = if (person) safePadding.end + 16.dp else 0.dp,
                bottom = safePadding.bottom + 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        userScrollEnabled = false,
    ) {
        item {
            val imageModifier =
                if (person) Modifier.size(width = 160.dp, height = 240.dp)
                else Modifier.fillMaxWidth().height(240.dp)
            if (sourceImage != null) {
                RetainedTransitionImage(
                    sourceImage,
                    imageModifier.sharedTransformImage(sourceImage.identity),
                )
            } else SkeletonBlock(imageModifier)
        }
        item {
            if (season) {
                Row(
                    modifier = bodyPadding.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SkeletonBlock(Modifier.width(100.dp).aspectRatio(0.66f))
                    Column(Modifier.weight(1f)) {
                        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(24.dp))
                        LoadingDetailTitle(Modifier)
                    }
                }
            } else LoadingDetailTitle(bodyPadding)
        }
        item { SkeletonBlock(bodyPadding.fillMaxWidth(0.45f).height(20.dp)) }
        if (!person) {
            item { SkeletonBlock(bodyPadding.fillMaxWidth().height(56.dp)) }
            item {
                Row(
                    modifier = bodyPadding.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(2) { SkeletonBlock(Modifier.weight(1f).height(48.dp)) }
                }
            }
        }
        item { TextSkeleton(bodyPadding) }
    }
}

@Composable
fun TextSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(16.dp)) }
        SkeletonBlock(Modifier.fillMaxWidth(0.65f).height(16.dp))
    }
}

@Composable
fun ListSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        userScrollEnabled = false,
    ) {
        items(5) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SkeletonBlock(Modifier.size(width = 120.dp, height = 80.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth().height(24.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.65f).height(16.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LoadingDetailTitle(modifier: Modifier) {
    val title = currentContainerTransformText()
    if (title != null) {
        Text(
            text = title.text,
            modifier = modifier.sharedTransformText(title.identity).semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    } else SkeletonBlock(modifier.fillMaxWidth(0.75f).height(36.dp))
}
