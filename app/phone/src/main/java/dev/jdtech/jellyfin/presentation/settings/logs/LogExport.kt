package dev.jdtech.jellyfin.presentation.settings.logs

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object LogExport {
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", file)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Findroid logs", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(intent, null))
    }

    suspend fun save(context: Context, file: File, destination: Uri) =
        withContext(Dispatchers.IO) {
            val output =
                context.contentResolver.openOutputStream(destination, "w")
                    ?: throw IOException("Unable to open log destination")
            output.use { stream -> file.inputStream().use { it.copyTo(stream) } }
        }
}
