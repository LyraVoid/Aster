package me.bmax.apatch.util

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.net.toUri
import me.bmax.apatch.apApp
import androidx.core.content.ContextCompat
import java.io.File

@SuppressLint("Range")
fun download(
    context: Context,
    url: String,
    fileName: String,
    description: String,
    onDownloaded: (Uri) -> Unit = {},
    onDownloading: () -> Unit = {},
    mimeType: String = "application/zip",
) {
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val query = DownloadManager.Query()

    query.setFilterByStatus(DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_PENDING)
    downloadManager.query(query)?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_ID))
            val uri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI))
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            val columnTitle = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE))
            if (url == uri || fileName == columnTitle) {
                if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                    onDownloading()
                    return
                } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    downloadManager.readableUri(cursor, id)?.let(onDownloaded)
                    return
                }
            }
        }
    }

    val request = DownloadManager.Request(url.toUri())
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setMimeType(mimeType).setTitle(fileName).setDescription(description)

    try {
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
    } catch (_: SecurityException) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (dir != null) {
            request.setDestinationUri(Uri.fromFile(File(dir, fileName)))
        } else {
            request.setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS, fileName
            )
        }
    }

    downloadManager.enqueue(request)
}

fun checkNewVersion(): LatestVersionInfo {
    val url = "https://api.github.com/repos/bmax121/APatch/releases/latest"
    val defaultValue = LatestVersionInfo()
    runCatching {
        apApp.okhttpClient.newCall(okhttp3.Request.Builder().url(url).build()).execute()
            .use { response ->
                if (!response.isSuccessful) {
                    return defaultValue
                }
                val body = response.body.string()

                val json = org.json.JSONObject(body)
                val changelog = json.optString("body")
                val versionCode = json.getInt("name")

                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (!name.endsWith(".apk")) {
                        continue
                    }
                    val downloadUrl = asset.getString("browser_download_url")

                    return LatestVersionInfo(
                        versionCode, downloadUrl, changelog
                    )
                }
            }
    }
    return defaultValue
}

/**
 * A finished download belongs in the public Download folder, where the reader can find it too, and
 * on a current Android nothing but the download manager itself may read from there. The uri it
 * hands out for a download it made on our behalf is the readable one; the plain file path is what
 * older releases returned, and is kept as the fallback.
 */
@SuppressLint("Range")
private fun DownloadManager.readableUri(cursor: Cursor, id: Long): Uri? =
    getUriForDownloadedFile(id)
        ?: cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))?.toUri()

@Composable
fun DownloadListener(context: Context, onDownloaded: (Uri) -> Unit) {
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            @SuppressLint("Range")
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    val id = intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID, -1
                    )
                    val downloadManager = context?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    val query = DownloadManager.Query().setFilterById(id)

                    downloadManager?.query(query)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                val id = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_ID))
                                downloadManager.readableUri(cursor, id)?.let { onDownloaded(it) }
                            }
                        }
                    }
                }
            }
        }
        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            context,
            receiver,
            intentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}
