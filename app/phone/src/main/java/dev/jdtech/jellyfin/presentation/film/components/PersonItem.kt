package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.dummy.dummyPerson
import dev.jdtech.jellyfin.core.presentation.utils.ContainerTransformImage
import dev.jdtech.jellyfin.core.presentation.utils.ContainerTransformText
import dev.jdtech.jellyfin.core.presentation.utils.containerTransform
import dev.jdtech.jellyfin.core.presentation.utils.containerTransformOnClick
import dev.jdtech.jellyfin.core.presentation.utils.rememberContainerTransformKey
import dev.jdtech.jellyfin.core.presentation.utils.sharedTransformText
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun PersonItem(person: FindroidItemPerson, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transformKey = rememberContainerTransformKey(person.id)
    var transitionImage by remember(person.id) { mutableStateOf<ContainerTransformImage?>(null) }
    val transformedOnClick =
        containerTransformOnClick(
            transformKey,
            transitionImage,
            ContainerTransformText(person.id, person.name),
            onClick,
        )
    Column(
        modifier =
            modifier
                .width(110.dp)
                .containerTransform(transformKey)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = transformedOnClick)
    ) {
        SourceTransitionImage(
            model = person.image.uri,
            identity = person.id,
            transformKey = transformKey,
            onImageReady = { transitionImage = it },
            modifier =
                Modifier.clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .fillMaxWidth()
                    .height(160.dp),
        )
        Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
        Text(
            text = person.name,
            modifier = Modifier.sharedTransformText(person.id, sourceKey = transformKey),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = person.role,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
@Preview(showBackground = true)
private fun PersonItemPreview() {
    FindroidTheme { PersonItem(person = dummyPerson, onClick = {}) }
}
