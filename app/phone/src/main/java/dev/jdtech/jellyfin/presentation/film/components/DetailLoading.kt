package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.jdtech.jellyfin.presentation.components.ErrorDialog

@Composable
fun DetailLoading(error: Exception?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    var showError by remember(error) { mutableStateOf(false) }
    if (error == null) {
        CircularProgressIndicator(modifier = modifier)
    } else {
        ErrorCard(
            onShowStacktrace = { showError = true },
            onRetryClick = onRetry,
            modifier = modifier,
        )
        if (showError) {
            ErrorDialog(exception = error, onDismissRequest = { showError = false })
        }
    }
}
