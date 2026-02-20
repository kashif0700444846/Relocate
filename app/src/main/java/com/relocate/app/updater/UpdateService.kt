// [Relocate] [UpdateService.kt] - GitHub Release Auto-Updater
// Author: AI
// Checks the GitHub API for the latest release, compares version names,
// downloads the APK, and triggers the install intent.

package com.relocate.app.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "[UpdateService]"
private const val GITHUB_OWNER = "kashif0700444846"
private const val GITHUB_REPO = "Relocate"
private const val API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

// ── GitHub API response models ──────────────────────────────────────────────────

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("content_type") val contentType: String?
)

// ── Update check result ─────────────────────────────────────────────────────────

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val releaseName: String?,
    val releaseNotes: String?,
    val apkUrl: String?,
    val apkSize: Long,
    val htmlUrl: String
)

object UpdateService {

    private val client = OkHttpClient()
    private val gson = Gson()

    /**
     * Checks GitHub for the latest release and compares with the current app version.
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val currentVersion = getCurrentVersion(context)
            Log.i(TAG, "[CheckUpdate] [START] Current version: $currentVersion")

            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "[CheckUpdate] [ERROR] HTTP ${response.code}")
                return@withContext UpdateInfo(
                    isUpdateAvailable = false,
                    currentVersion = currentVersion,
                    latestVersion = "unknown",
                    releaseName = null,
                    releaseNotes = null,
                    apkUrl = null,
                    apkSize = 0,
                    htmlUrl = ""
                )
            }

            val body = response.body?.string() ?: ""
            val release = gson.fromJson(body, GitHubRelease::class.java)
            val latestVersion = release.tagName.removePrefix("v")

            // Find the APK asset
            val apkAsset = release.assets.firstOrNull { asset ->
                asset.name.endsWith(".apk", ignoreCase = true)
            }

            val isNewer = isVersionNewer(latestVersion, currentVersion)
            Log.i(TAG, "[CheckUpdate] [SUCCESS] Latest: $latestVersion, Newer: $isNewer, APK: ${apkAsset?.name}")

            UpdateInfo(
                isUpdateAvailable = isNewer,
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                releaseName = release.name,
                releaseNotes = release.body,
                apkUrl = apkAsset?.downloadUrl,
                apkSize = apkAsset?.size ?: 0,
                htmlUrl = release.htmlUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "[CheckUpdate] [ERROR] ${e.message}")
            UpdateInfo(
                isUpdateAvailable = false,
                currentVersion = getCurrentVersion(context),
                latestVersion = "error",
                releaseName = null,
                releaseNotes = "Failed to check: ${e.message}",
                apkUrl = null,
                apkSize = 0,
                htmlUrl = ""
            )
        }
    }

    /**
     * Downloads the APK via DownloadManager and triggers install when complete.
     */
    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        version: String,
        onProgress: (DownloadStatus) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            onProgress(DownloadStatus.Downloading(0))
            Log.i(TAG, "[Download] [START] $apkUrl")

            val fileName = "Relocate-v${version}.apk"

            // Clean up old APKs
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir?.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { it.delete() }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Relocate v$version")
                .setDescription("Downloading update...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive")

            val downloadId = downloadManager.enqueue(request)
            Log.i(TAG, "[Download] [ENQUEUED] ID: $downloadId")

            // Wait for download completion
            val success = waitForDownload(context, downloadId, onProgress)

            if (success) {
                val apkFile = File(downloadsDir, fileName)
                if (apkFile.exists()) {
                    Log.i(TAG, "[Download] [SUCCESS] File: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
                    onProgress(DownloadStatus.Installing)
                    installApk(context, apkFile)
                    onProgress(DownloadStatus.Done)
                } else {
                    Log.e(TAG, "[Download] [ERROR] APK file not found after download")
                    onProgress(DownloadStatus.Failed("APK file not found after download"))
                }
            } else {
                onProgress(DownloadStatus.Failed("Download failed or cancelled"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Download] [ERROR] ${e.message}")
            onProgress(DownloadStatus.Failed(e.message ?: "Unknown error"))
        }
    }

    /**
     * Suspends until DownloadManager finishes downloading the given ID.
     */
    private suspend fun waitForDownload(
        context: Context,
        downloadId: Long,
        onProgress: (DownloadStatus) -> Unit
    ): Boolean = suspendCancellableCoroutine { cont ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                if (id == downloadId) {
                    try { context.unregisterReceiver(this) } catch (_: Exception) {}

                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = dm.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusIdx)
                        cursor.close()
                        if (cont.isActive) {
                            cont.resume(status == DownloadManager.STATUS_SUCCESSFUL)
                        }
                    } else {
                        cursor?.close()
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }

        cont.invokeOnCancellation {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    /**
     * Launches the system installer for the given APK file.
     */
    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            Log.i(TAG, "[Install] [SUCCESS] Install intent launched")
        } catch (e: Exception) {
            Log.e(TAG, "[Install] [ERROR] ${e.message}")
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    /**
     * Compares semver strings. Returns true if [latest] > [current].
     */
    private fun isVersionNewer(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

            for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "[VersionCompare] [ERROR] $latest vs $current: ${e.message}")
        }
        return false
    }

    // ── Download status sealed class ────────────────────────────────────────────

    sealed class DownloadStatus {
        data class Downloading(val percent: Int) : DownloadStatus()
        data object Installing : DownloadStatus()
        data object Done : DownloadStatus()
        data class Failed(val error: String) : DownloadStatus()
    }
}
