package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.core.presentation.dummy.dummyPersonDetail
import dev.jdtech.jellyfin.core.presentation.utils.sharedTransformText
import dev.jdtech.jellyfin.film.presentation.person.PersonAction
import dev.jdtech.jellyfin.film.presentation.person.PersonState
import dev.jdtech.jellyfin.film.presentation.person.PersonViewModel
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidPerson
import dev.jdtech.jellyfin.presentation.components.DetailSkeleton
import dev.jdtech.jellyfin.presentation.film.components.DetailTransitionImage
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.ItemCard
import dev.jdtech.jellyfin.presentation.film.components.ItemTopBar
import dev.jdtech.jellyfin.presentation.film.components.OverviewText
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import java.util.UUID

@Composable
fun PersonScreen(
    personId: UUID,
    navigateBack: () -> Unit,
    navigateHome: () -> Unit,
    navigateToItem: (item: FindroidItem) -> Unit,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadPerson(personId) }

    PersonScreenLayout(
        state = state,
        onAction = { action ->
            when (action) {
                is PersonAction.NavigateBack -> navigateBack()
                is PersonAction.NavigateHome -> navigateHome()
                is PersonAction.NavigateToItem -> navigateToItem(action.item)
            }
        },
    )
}

@Composable
private fun PersonScreenLayout(state: PersonState, onAction: (PersonAction) -> Unit) {
    val safePadding = rememberSafePadding()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingTop = safePadding.top + 72.dp
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default

    val itemsPadding = PaddingValues(start = paddingStart, end = paddingEnd)

    Box(modifier = Modifier.fillMaxSize()) {
        state.person?.let { person ->
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(paddingTop))
                Surface(
                    modifier = Modifier.padding(itemsPadding),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    when {
                        windowSizeClass.isWidthAtLeastBreakpoint(
                            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
                        ) -> {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
                                horizontalArrangement =
                                    Arrangement.spacedBy(MaterialTheme.spacings.default),
                            ) {
                                PersonImage(person)
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement =
                                        Arrangement.spacedBy(MaterialTheme.spacings.medium),
                                ) {
                                    Text(
                                        text = person.name,
                                        modifier =
                                            Modifier.sharedTransformText(person.id).semantics {
                                                heading()
                                            },
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                    if (person.overview.isNotBlank()) {
                                        OverviewText(text = person.overview, maxCollapsedLines = 12)
                                    }
                                }
                            }
                        }
                        else -> {
                            Column(
                                modifier =
                                    Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
                                verticalArrangement =
                                    Arrangement.spacedBy(MaterialTheme.spacings.medium),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                PersonImage(person)
                                Text(
                                    text = person.name,
                                    modifier =
                                        Modifier.sharedTransformText(person.id).semantics {
                                            heading()
                                        },
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                                if (person.overview.isNotBlank()) {
                                    OverviewText(text = person.overview, maxCollapsedLines = 4)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(MaterialTheme.spacings.large))

                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default)) {
                    if (state.starredInMovies.isNotEmpty()) {
                        Column {
                            Text(
                                text = stringResource(CoreR.string.movies_label),
                                modifier = Modifier.padding(itemsPadding).semantics { heading() },
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
                            LazyRow(
                                contentPadding = itemsPadding,
                                horizontalArrangement =
                                    Arrangement.spacedBy(MaterialTheme.spacings.default),
                            ) {
                                items(state.starredInMovies, key = { it.id }) { item ->
                                    ItemCard(
                                        item = item,
                                        direction = Direction.VERTICAL,
                                        onClick = { onAction(PersonAction.NavigateToItem(item)) },
                                    )
                                }
                            }
                        }
                    }

                    if (state.starredInShows.isNotEmpty()) {
                        Column {
                            Text(
                                text = stringResource(CoreR.string.shows_label),
                                modifier = Modifier.padding(itemsPadding).semantics { heading() },
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
                            LazyRow(
                                contentPadding = itemsPadding,
                                horizontalArrangement =
                                    Arrangement.spacedBy(MaterialTheme.spacings.default),
                            ) {
                                items(state.starredInShows, key = { it.id }) { item ->
                                    ItemCard(
                                        item = item,
                                        direction = Direction.VERTICAL,
                                        onClick = { onAction(PersonAction.NavigateToItem(item)) },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(paddingBottom))
            }
        } ?: DetailSkeleton(person = true)

        ItemTopBar(
            hasBackButton = true,
            hasHomeButton = true,
            onBackClick = { onAction(PersonAction.NavigateBack) },
            onHomeClick = { onAction(PersonAction.NavigateHome) },
        )
    }
}

@Composable
private fun PersonImage(person: FindroidPerson, modifier: Modifier = Modifier) {
    DetailTransitionImage(
        identity = person.id,
        model = person.images.primary,
        modifier =
            modifier
                .height(240.dp)
                .aspectRatio(0.66f)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainer),
    )
}

@PreviewScreenSizes
@Composable
private fun PersonScreenLayoutPreview() {
    FindroidTheme {
        PersonScreenLayout(
            state = PersonState(person = dummyPersonDetail, starredInMovies = dummyMovies),
            onAction = {},
        )
    }
}
