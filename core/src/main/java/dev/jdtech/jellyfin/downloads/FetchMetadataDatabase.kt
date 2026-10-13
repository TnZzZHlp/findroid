package dev.jdtech.jellyfin.downloads

import android.content.Context
import com.tonyodev.fetch2.database.DownloadDatabase
import com.tonyodev.fetch2.database.DownloadInfo
import com.tonyodev.fetch2.database.FetchDatabaseManager
import com.tonyodev.fetch2.database.FetchDatabaseManagerImpl
import com.tonyodev.fetch2.fetch.LiveSettings
import com.tonyodev.fetch2core.DefaultStorageResolver
import com.tonyodev.fetch2core.Extras
import com.tonyodev.fetch2core.FetchLogger
import com.tonyodev.fetch2core.getFileTempDir

class FetchMetadataDatabase
private constructor(private val store: FetchDatabaseManager<DownloadInfo>) :
    FetchDatabaseManager<DownloadInfo> by store {
    constructor(
        context: Context,
        namespace: String,
    ) : this(
        FetchDatabaseManagerImpl(
            context,
            namespace,
            FetchLogger(false, namespace),
            DownloadDatabase.getMigrations(),
            LiveSettings(namespace),
            false,
            DefaultStorageResolver(context, getFileTempDir(context)),
        )
    )

    override fun updateExtras(id: Int, extras: Extras): DownloadInfo? {
        val download = store.get(id) ?: return null
        download.extras = extras
        store.update(download)
        return store.get(id)
    }
}
