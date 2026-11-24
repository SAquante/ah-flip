package com.bbb.flip

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates from GitHub Releases.
 *
 * The app is not on Google Play, so the only honest way to ship fixes is to look at the project's
 * own releases feed: newest tag, its APK asset, and download that APK when the tag is newer than
 * the installed build.
 */
object Updates {

    private const val TAG = "BBB"
    private const val RELEASES = "https://api.github.com/repos/SAquante/ah-flip/releases/latest"

    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long,
    )

    /** Null when there is no newer release (or the check failed — updates are best-effort). */
    suspend fun check(current: String = BuildConfig.VERSION_NAME): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(RELEASES).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("accept", "application/vnd.github+json")
                setRequestProperty("user-agent", "AH-Flip/${BuildConfig.VERSION_NAME}")
            }
            try {
                val code = conn.responseCode
                Log.d(TAG, "update check: HTTP $code (installed $current)")
                if (code != 200) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = Json.parseToJsonElement(body).jsonObject
                val tag = root["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return@runCatching null
                Log.d(TAG, "update check: latest tag $tag, installed $current")
                if (!isNewer(tag, current)) return@runCatching null
                val apk = (root["assets"] as? JsonArray)?.map { it.jsonObject }
                    ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                    ?: return@runCatching null
                Release(
                    version = tag,
                    notes = root["body"]?.jsonPrimitive?.content.orEmpty().trim(),
                    apkUrl = apk["browser_download_url"]!!.jsonPrimitive.content,
                    sizeBytes = apk["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** "1.10" is newer than "1.9"; missing parts count as zero. */
    internal fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-')
            .map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * Downloads the APK and opens the system installer. The file goes to app-specific external
     * storage, which needs no permission on any supported version, and is shared through a
     * FileProvider because Android refuses `file://` install intents.
     */
    fun downloadAndInstall(context: Context, release: Release) {
        val target = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ah-flip-${release.version}.apk")
        if (target.exists()) target.delete()

        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("AH Флип ${release.version}")
            .setDescription("Загрузка обновления")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, target.name)

        val dm = context.getSystemService(DownloadManager::class.java)
            ?: return openInBrowser(context, release)
        val id = dm.enqueue(request)

        // Install once the download finishes; the receiver unregisters itself.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                runCatching { context.unregisterReceiver(this) }
                install(context, target)
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED,
        )
    }

    private fun install(context: Context, apk: File) {
        runCatching {
            if (!apk.exists()) throw IllegalStateException("APK не скачался")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Log.w(TAG, "install failed: ${it.message}")
            // Fall back to the release page, where the APK can be downloaded by hand.
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SAquante/ah-flip/releases/latest"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun openInBrowser(context: Context, release: Release) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
