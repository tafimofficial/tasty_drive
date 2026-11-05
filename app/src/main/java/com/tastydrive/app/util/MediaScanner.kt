package com.tastydrive.app.util

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.tastydrive.app.data.AppPreferences
import com.tastydrive.app.data.FileCategory
import com.tastydrive.app.data.MediaItem
import com.tastydrive.app.data.UploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaScanner(private val context: Context) {
    private val prefs = AppPreferences.getInstance(context)

    suspend fun scanAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaMap = LinkedHashMap<Long, MediaItem>()
        val seenFileKeys = HashSet<String>()

        // 1. Unified scan across MediaStore.Files (covers Images, Videos, Audio, PDFs, APKs, Docs, Zips)
        try {
            val fileProjection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_ADDED
            )
            val fileSortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            val externalFilesUri = MediaStore.Files.getContentUri("external")

            context.contentResolver.query(
                externalFilesUri,
                fileProjection,
                "${MediaStore.Files.FileColumns.SIZE} > 0",
                null,
                fileSortOrder
            )?.use { cursor ->
                extractMediaFromCursor(cursor, externalFilesUri, mediaMap, seenFileKeys)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // 2. Ensure MediaStore Images are fully covered
        try {
            val imgProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_ADDED
            )
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imgProjection,
                "${MediaStore.Images.Media.SIZE} > 0",
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                extractMediaFromCursor(cursor, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaMap, seenFileKeys)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // 3. Ensure MediaStore Videos are fully covered
        try {
            val vidProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATE_ADDED
            )
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                vidProjection,
                "${MediaStore.Video.Media.SIZE} > 0",
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                extractMediaFromCursor(cursor, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaMap, seenFileKeys)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // 4. Ensure MediaStore Audio is fully covered
        try {
            val audioProjection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATE_ADDED
            )
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                "${MediaStore.Audio.Media.SIZE} > 0",
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                extractMediaFromCursor(cursor, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaMap, seenFileKeys)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // 5. Scan common directories for standalone APKs, PDFs, Docs in Downloads & Documents folders
        try {
            scanDirectoryFallback(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), mediaMap, seenFileKeys)
            scanDirectoryFallback(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), mediaMap, seenFileKeys)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        val resultList = mediaMap.values.toMutableList()
        try {
            resultList.sortByDescending { it.dateAdded }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return@withContext resultList
    }

    private fun extractMediaFromCursor(
        cursor: Cursor,
        baseUri: Uri,
        outMap: MutableMap<Long, MediaItem>,
        seenFileKeys: MutableSet<String>
    ) {
        val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
        val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
        val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)

        if (idCol == -1 || sizeCol == -1) return

        while (cursor.moveToNext()) {
            try {
                val id = cursor.getLong(idCol)
                val size = cursor.getLong(sizeCol)
                if (size <= 0) continue

                val path = if (pathCol != -1) {
                    try { cursor.getString(pathCol) } catch (_: Throwable) { null }
                } else null

                val rawName = if (nameCol != -1) {
                    try { cursor.getString(nameCol) } catch (_: Throwable) { null }
                } else null

                val name = rawName ?: (path?.let { File(it).name } ?: "file_$id")

                // Strict deduplication key: file name + size
                val dedupeKey = "${name}_$size"
                if (seenFileKeys.contains(dedupeKey)) {
                    continue
                }
                seenFileKeys.add(dedupeKey)

                val rawMime = if (mimeCol != -1) {
                    try { cursor.getString(mimeCol) } catch (_: Throwable) { null }
                } else null

                val mimeType = rawMime ?: getMimeTypeFromExtension(name)
                val date = if (dateCol != -1) {
                    try { cursor.getLong(dateCol) } catch (_: Throwable) { System.currentTimeMillis() / 1000 }
                } else System.currentTimeMillis() / 1000

                val category = MediaItem.determineCategory(name, mimeType)
                val isVideo = category == FileCategory.VIDEOS
                val contentUri = ContentUris.withAppendedId(baseUri, id)

                val status = when {
                    size > (2000L * 1024L * 1024L) -> UploadStatus.OVERSIZED
                    prefs.isUploaded(id, name, size) -> UploadStatus.COMPLETED
                    else -> UploadStatus.QUEUED
                }

                outMap[id] = MediaItem(
                    id = id,
                    uri = contentUri,
                    displayName = name,
                    path = path,
                    size = size,
                    mimeType = mimeType,
                    dateAdded = date,
                    isVideo = isVideo,
                    category = category,
                    status = status
                )
            } catch (rowEx: Throwable) {
                // Ignore any corrupt or permission-denied row and continue scanning others
                continue
            }
        }
    }

    private fun scanDirectoryFallback(dir: File?, outMap: MutableMap<Long, MediaItem>, seenFileKeys: MutableSet<String>) {
        try {
            if (dir == null || !dir.exists() || !dir.canRead()) return
            val files = dir.listFiles() ?: return

            for (file in files) {
                try {
                    if (file.isDirectory) {
                        val subFiles = file.listFiles() ?: continue
                        for (sub in subFiles) {
                            try {
                                addFileIfEligible(sub, outMap, seenFileKeys)
                            } catch (_: Throwable) {}
                        }
                    } else {
                        addFileIfEligible(file, outMap, seenFileKeys)
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    private fun addFileIfEligible(file: File, outMap: MutableMap<Long, MediaItem>, seenFileKeys: MutableSet<String>) {
        try {
            if (!file.isFile || file.length() <= 0) return
            val name = file.name
            val size = file.length()

            val dedupeKey = "${name}_$size"
            if (seenFileKeys.contains(dedupeKey)) return
            seenFileKeys.add(dedupeKey)

            val path = file.absolutePath
            val mimeType = getMimeTypeFromExtension(name)
            val category = MediaItem.determineCategory(name, mimeType)
            val isVideo = category == FileCategory.VIDEOS

            val syntheticId = (path.hashCode().toLong() and 0x7FFFFFFF) or 0x40000000
            val uri = Uri.fromFile(file)

            val status = when {
                size > (2000L * 1024L * 1024L) -> UploadStatus.OVERSIZED
                prefs.isUploaded(syntheticId, name, size) -> UploadStatus.COMPLETED
                else -> UploadStatus.QUEUED
            }

            outMap[syntheticId] = MediaItem(
                id = syntheticId,
                uri = uri,
                displayName = name,
                path = path,
                size = size,
                mimeType = mimeType,
                dateAdded = file.lastModified() / 1000,
                isVideo = isVideo,
                category = category,
                status = status
            )
        } catch (_: Throwable) {}
    }

    private fun getMimeTypeFromExtension(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "application/octet-stream"
        if (ext == "apk") return "application/vnd.android.package-archive"
        if (ext == "pdf") return "application/pdf"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
