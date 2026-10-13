package dev.jdtech.jellyfin.presentation.film.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

@Composable
fun rememberDownloadStart(onDownload: (Int) -> Unit): (Int) -> Unit {
    val context = LocalContext.current
    var storageIndex by remember { mutableIntStateOf(0) }
    val launcher =
        if (!LocalInspectionMode.current) {
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                onDownload(storageIndex)
            }
        } else null
    return { index ->
        storageIndex = index
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED &&
                launcher != null
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else onDownload(index)
    }
}
