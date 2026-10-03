package dev.jdtech.jellyfin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun DetailLoading(error: Exception?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (error == null) {
        CircularProgressIndicator(modifier = modifier)
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        ) {
            Text(text = stringResource(CoreR.string.error_loading_data))
            Button(onClick = onRetry) { Text(text = stringResource(CoreR.string.retry)) }
        }
    }
}
