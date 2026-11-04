package com.tastydrive.app.data

import android.net.Uri

enum class UploadStatus {
    QUEUED,
    UPLOADING,
    COMPLETED,
    PAUSED,
    FAILED,
    OVERSIZED
}

enum class FileCategory {
    ALL,
    PHOTOS,
    VIDEOS,
    APKS,
    PDFS,
    DOCUMENTS,
    AUDIO,
    OTHER
}

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val path: String?,
    val size: Long,
    val mimeType: String,
    val dateAdded: Long,
    val isVideo: Boolean = false,
    val category: FileCategory = FileCategory.OTHER,
    var status: UploadStatus = UploadStatus.QUEUED,
    var progress: Int = 0,
    var speed: String = "",
    var errorMessage: String? = null,
    var isSelected: Boolean = false,
    var checksum: Long = 0L
) {
    val isOver50MB: Boolean
        get() = size > (50L * 1024L * 1024L)

    companion object {
        fun determineCategory(fileName: String, mimeType: String): FileCategory {
            val lowerName = fileName.lowercase()
            val lowerMime = mimeType.lowercase()
            return when {
                lowerName.endsWith(".apk") || lowerMime.contains("android.package-archive") -> FileCategory.APKS
                lowerName.endsWith(".pdf") || lowerMime.contains("pdf") -> FileCategory.PDFS
                lowerMime.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                        lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif") ||
                        lowerName.endsWith(".heic") -> FileCategory.PHOTOS
                lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") ||
                        lowerName.endsWith(".mov") || lowerName.endsWith(".avi") || lowerName.endsWith(".3gp") -> FileCategory.VIDEOS
                lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".m4a") ||
                        lowerName.endsWith(".wav") || lowerName.endsWith(".aac") || lowerName.endsWith(".flac") -> FileCategory.AUDIO
                lowerName.endsWith(".doc") || lowerName.endsWith(".docx") || lowerName.endsWith(".txt") ||
                        lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") || lowerName.endsWith(".ppt") ||
                        lowerName.endsWith(".pptx") || lowerName.endsWith(".zip") || lowerName.endsWith(".rar") ||
                        lowerName.endsWith(".7z") || lowerMime.contains("document") || lowerMime.contains("zip") -> FileCategory.DOCUMENTS
                else -> FileCategory.OTHER
            }
        }
    }
}

data class UploadStats(
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val pendingCount: Int = 0,
    val oversizedCount: Int = 0,
    val failedCount: Int = 0,
    val totalBytes: Long = 0L,
    val uploadedBytes: Long = 0L,
    val currentSpeedStr: String = "0 B/s",
    val isUploading: Boolean = false,
    val isPaused: Boolean = false
) {
    val progressPercent: Int
        get() = if (totalCount > 0) ((completedCount * 100) / totalCount).coerceIn(0, 100) else 0
}
