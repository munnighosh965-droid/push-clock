package com.powerclock.alarm.data.audio

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class AudioTrackInfo(
    val title: String,
    val artist: String,
    val durationMs: Long,
)

/**
 * Validation, metadata, and optional private copies of user-selected alarm
 * music. Files are only ever read through the Storage Access Framework and
 * never leave the device.
 */
@Singleton
class CustomAudioStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val privateDir: File
        get() = File(context.filesDir, "custom_audio").apply { mkdirs() }

    /** Take long-lived read access for a SAF-picked document when possible. */
    fun persistPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some providers do not offer persistable grants; the copy
            // option below still guarantees reliability.
        }
    }

    /**
     * True when the URI can currently be opened for reading. Called before
     * scheduling and before playback; failures route to the bundled tone.
     */
    suspend fun isPlayable(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (!isValidScheme(uri)) return@withContext false
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.read() >= -1
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** Only content:// (SAF) and our own private file:// copies are accepted. */
    fun isValidScheme(uri: Uri): Boolean = when (uri.scheme) {
        "content" -> true
        "file" -> uri.path?.startsWith(privateDir.absolutePath) == true
        else -> false
    }

    suspend fun readMetadata(uri: Uri): AudioTrackInfo? = withContext(Dispatchers.IO) {
        if (!isValidScheme(uri)) return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            AudioTrackInfo(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: uri.lastPathSegment?.substringAfterLast('/')?.take(60)
                    ?: "Selected track",
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: "Unknown artist",
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
            )
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Copies the picked track into app-private storage so the alarm keeps
     * working even if the original file is moved or deleted. Returns the
     * new private URI, or null when space is insufficient or the copy fails.
     */
    suspend fun copyIntoPrivateStorage(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        if (!isValidScheme(uri)) return@withContext null
        try {
            val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                ?: -1L
            val stat = StatFs(context.filesDir.absolutePath)
            val available = stat.availableBytes
            // Require the file size plus a 50 MB headroom.
            if (size > 0 && available < size + 50L * 1024 * 1024) return@withContext null

            // A single private copy at a time keeps storage predictable.
            privateDir.listFiles()?.forEach { it.delete() }
            val target = File(privateDir, "alarm_track_${System.currentTimeMillis()}.audio")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            Uri.fromFile(target)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clearPrivateCopies() = withContext(Dispatchers.IO) {
        privateDir.listFiles()?.forEach { it.delete() }
    }
}
