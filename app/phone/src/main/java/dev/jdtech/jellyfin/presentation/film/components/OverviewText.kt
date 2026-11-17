package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun OverviewText(text: String, maxCollapsedLines: Int = Int.MAX_VALUE) {
    if (text.isBlank()) return

    var canExpand by remember(text, maxCollapsedLines) { mutableStateOf(false) }
    var isOverviewExpanded by remember(text, maxCollapsedLines) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacings.medium).animateContentSize()) {
            Text(
                text = text,
                overflow = TextOverflow.Ellipsis,
                maxLines = if (isOverviewExpanded) Int.MAX_VALUE else maxCollapsedLines,
                onTextLayout = { result ->
                    if (!isOverviewExpanded) canExpand = result.hasVisualOverflow
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (canExpand) {
                TextButton(
                    onClick = { isOverviewExpanded = !isOverviewExpanded },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        stringResource(
                            if (isOverviewExpanded) CoreR.string.collapse_overview
                            else CoreR.string.expand_overview
                        )
                    )
                    Icon(
                        painterResource(
                            if (isOverviewExpanded) CoreR.drawable.ic_chevron_up
                            else CoreR.drawable.ic_chevron_down
                        ),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun OverviewTextPreview() {
    FindroidTheme { OverviewText(text = dummyMovie.overview, maxCollapsedLines = 3) }
}
