package com.spacecamera.data.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoStorage(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    suspend fun createVideoFile(): File = withContext(Dispatchers.IO) {
        val videoFileName = "VIDEO_${dateFormat.format(Date())}.mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use scoped storage
            val videosDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: throw Exception("Unable to get Videos directory")
            File(videosDir, videoFileName)
        } else {
            // Legacy storage
            val videosDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (!videosDir.exists()) {
                videosDir.mkdirs()
            }
            File(videosDir, videoFileName)
        }
    }

    suspend fun saveVideoToGallery(videoFile: File): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped storage
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, videoFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                }

                val videoUri = context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: throw Exception("Failed to create MediaStore entry")

                context.contentResolver.openOutputStream(videoUri)?.use { outputStream ->
                    videoFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                true
            } else {
                // Legacy storage - already saved in public directory
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "evt=save_video_failed name=%s", videoFile.name)
            false
        }
    }

    suspend fun getVideoFileSize(videoFile: File): Long = withContext(Dispatchers.IO) {
        return@withContext videoFile.length()
    }

    suspend fun formatFileSize(sizeInBytes: Long): String = withContext(Dispatchers.IO) {
        return@withContext when {
            sizeInBytes <= 0 -> "0 B"
            sizeInBytes < 1024 -> "$sizeInBytes B"
            sizeInBytes < 1024 * 1024 -> String.format("%.2f KB", sizeInBytes / 1024.0)
            sizeInBytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", sizeInBytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", sizeInBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
