package com.cautious5.crisis_coach.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import com.cautious5.crisis_coach.model.ai.ModelVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModelDownloader(
    private val context: Context,
    private val authManager: HuggingFaceAuthManager
) {

    companion object {
        private const val TAG = "ModelDownloader"
    }

    private val _downloadProgress = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadProgress: StateFlow<DownloadState> = _downloadProgress.asStateFlow()

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var currentDownloadId: Long? = null

    sealed class DownloadState {
        data object Idle : DownloadState()
        data object Preparing : DownloadState()
        data class InProgress(val progress: Int, val bytesDownloaded: Long, val totalSize: Long) : DownloadState()
        data class Completed(val filePath: String) : DownloadState()
        data class Failed(val error: String) : DownloadState()
        data object AuthRequired : DownloadState()
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == currentDownloadId) {
                checkDownloadStatus(downloadId)
            }
        }
    }

    fun startDownload(modelVariant: ModelVariant) {
        val cookies = authManager.getStoredCookies()
        val token = authManager.getToken()

        if (token.isNullOrBlank()) {
            _downloadProgress.value = DownloadState.AuthRequired
            Log.e(TAG, "No HF access-token found; prompting user.")
            return
        }

        _downloadProgress.value = DownloadState.Preparing

        val downloadUrl = modelVariant.getDownloadUrl()
        val fileName = modelVariant.downloadFileName
        Log.d(TAG, "Starting download: $downloadUrl")

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Downloading ${modelVariant.displayName}")
            .setDescription("Crisis Coach AI Model")
            .addRequestHeader("Authorization", "Bearer $token") // USE BEARER TOKEN
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName) // USE APP-SPECIFIC DIR
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            .setAllowedOverMetered(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        // Register receiver
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        context.registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            flags
        )

        // Enqueue
        currentDownloadId = downloadManager.enqueue(request)
        Log.d(TAG, "Download enqueued with ID: $currentDownloadId")

        // Poll status immediately to catch instant failures
        currentDownloadId?.let { checkDownloadStatus(it) }
    }

    private fun checkDownloadStatus(downloadId: Long) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
            val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalSizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

            val status = cursor.getInt(statusIndex)
            val reason = cursor.getInt(reasonIndex)
            val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
            val totalSize = cursor.getLong(totalSizeIndex)
            val localUri = cursor.getString(uriIndex)

            when (status) {
                DownloadManager.STATUS_RUNNING -> {
                    val progress = if (totalSize > 0) {
                        ((bytesDownloaded * 100) / totalSize).toInt()
                    } else 0
                    _downloadProgress.value = DownloadState.InProgress(progress, bytesDownloaded, totalSize)
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    _downloadProgress.value = DownloadState.Completed(localUri ?: "")
                    cleanup()
                }
                DownloadManager.STATUS_FAILED -> {
                    val errorMessage = when (reason) {
                        DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
                        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage device not found"
                        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
                        DownloadManager.ERROR_FILE_ERROR -> "File error"
                        DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
                        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
                        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
                        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP response code"
                        DownloadManager.ERROR_UNKNOWN -> "Unknown error"
                        else -> "Download failed (reason: $reason)"
                    }
                    _downloadProgress.value = DownloadState.Failed(errorMessage)
                    cleanup()
                }
            }
        }
        cursor.close()
    }

    private fun cleanup() {
        try {
            context.unregisterReceiver(downloadReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
        currentDownloadId = null
    }

    fun cancel() {
        currentDownloadId?.let { downloadManager.remove(it) }
        cleanup()
        _downloadProgress.value = DownloadState.Idle
    }
}