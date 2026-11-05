package com.tastydrive.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tastydrive.app.data.AppPreferences
import com.tastydrive.app.data.FileCategory
import com.tastydrive.app.data.MediaItem
import com.tastydrive.app.data.UploadStats
import com.tastydrive.app.data.UploadStatus
import com.tastydrive.app.service.ApiResult
import com.tastydrive.app.service.TelegramApiClient
import com.tastydrive.app.service.UploadManager
import com.tastydrive.app.util.LocalServerDiscovery
import com.tastydrive.app.util.MediaScanner
import com.tastydrive.app.util.NativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GalleryFilter {
    ALL,
    PHOTOS,
    VIDEOS,
    APKS,
    PDFS,
    DOCS,
    AUDIO,
    PENDING,
    UPLOADED
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val prefs = AppPreferences.getInstance(application)
    val uploadManager = UploadManager.getInstance(application)
    private val mediaScanner = MediaScanner(application)
    private val apiClient = TelegramApiClient(application)

    private val _galleryFilter = MutableStateFlow(GalleryFilter.ALL)
    val galleryFilter: StateFlow<GalleryFilter> = _galleryFilter

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    val stats: StateFlow<UploadStats> = uploadManager.stats

    // Local Server Connection Live Status
    val isLocalConnected: StateFlow<Boolean> = uploadManager.isLocalConnected
    val localServerStatusText: StateFlow<String> = uploadManager.localServerStatusText
    val localServerUrl: StateFlow<String> = uploadManager.localServerUrl

    // Filtered gallery items
    val galleryItems: StateFlow<List<MediaItem>> = combine(
        uploadManager.itemsFlow,
        _galleryFilter
    ) { items, filter ->
        when (filter) {
            GalleryFilter.ALL -> items
            GalleryFilter.PHOTOS -> items.filter { it.category == FileCategory.PHOTOS }
            GalleryFilter.VIDEOS -> items.filter { it.category == FileCategory.VIDEOS }
            GalleryFilter.APKS -> items.filter { it.category == FileCategory.APKS }
            GalleryFilter.PDFS -> items.filter { it.category == FileCategory.PDFS }
            GalleryFilter.DOCS -> items.filter { it.category == FileCategory.DOCUMENTS }
            GalleryFilter.AUDIO -> items.filter { it.category == FileCategory.AUDIO }
            GalleryFilter.PENDING -> items.filter { it.status == UploadStatus.QUEUED || it.status == UploadStatus.PAUSED }
            GalleryFilter.UPLOADED -> items.filter { it.status == UploadStatus.COMPLETED }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // High performance queue items: active uploading + failed + next 50 pending + 10 recent completed
    val queueItems: StateFlow<List<MediaItem>> = uploadManager.itemsFlow.map { items ->
        val uploadable = items.filter { !it.isOver2GB }
        val uploading = uploadable.filter { it.status == UploadStatus.UPLOADING }
        val failed = uploadable.filter { it.status == UploadStatus.FAILED }
        val queued = uploadable.filter { it.status == UploadStatus.QUEUED || it.status == UploadStatus.PAUSED }.take(50)
        val recentCompleted = uploadable.filter { it.status == UploadStatus.COMPLETED }.takeLast(10)
        uploading + failed + queued + recentCompleted
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Oversized large files (> 50MB)
    val largeFiles: StateFlow<List<MediaItem>> = uploadManager.itemsFlow.map { items ->
        items.filter { it.isOver50MB || it.status == UploadStatus.OVERSIZED }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun scanMedia() {
        if (_isScanning.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isScanning.value = true
                val scanned = mediaScanner.scanAllMedia()
                uploadManager.setAllMedia(scanned)
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun setGalleryFilter(filter: GalleryFilter) {
        _galleryFilter.value = filter
    }

    fun startOrResumeUploads() {
        if (uploadManager.isPaused.value) {
            uploadManager.resumeUploads()
        } else {
            uploadManager.enqueueAllPending()
        }
    }

    fun pauseUploads() {
        uploadManager.pauseUploads()
    }

    fun retryFailed() {
        uploadManager.retryFailed()
    }

    fun clearFailed() {
        uploadManager.clearFailed()
    }

    fun clearSingleFailed(item: MediaItem) {
        uploadManager.clearSingleFailed(item)
    }

    fun enqueueSingleItem(item: MediaItem) {
        uploadManager.enqueueSingle(item)
    }

    suspend fun testCloudBot(token: String): ApiResult<String> {
        return apiClient.testConnection(token, TelegramApiClient.CLOUD_API_BASE)
    }

    suspend fun testCloudChannel(token: String, channelId: String): ApiResult<String> {
        return apiClient.testChannel(token, channelId, TelegramApiClient.CLOUD_API_BASE)
    }

    suspend fun testLocalServer(serverUrl: String, token: String): ApiResult<String> {
        val normalized = TelegramApiClient.normalizeBaseUrl(serverUrl)
        return apiClient.testConnection(token, normalized)
    }

    suspend fun testLocalChannel(serverUrl: String, token: String, channelId: String): ApiResult<String> {
        val normalized = TelegramApiClient.normalizeBaseUrl(serverUrl)
        return apiClient.testChannel(token, channelId, normalized)
    }

    suspend fun autoDiscoverLocalServer(onProgress: ((checked: Int, total: Int) -> Unit)? = null): LocalServerDiscovery.DiscoveredServer? {
        return uploadManager.autoDiscoverAndConnectLocal(onProgress)
    }

    // Backward compatibility
    suspend fun testBot(token: String): ApiResult<String> = testCloudBot(token)
    suspend fun testChannel(token: String, channelId: String): ApiResult<String> = testCloudChannel(token, channelId)

    fun createTelegramShareIntent(selectedItems: List<MediaItem>): Intent {
        val context = getApplication<Application>()
        val pkg = com.tastydrive.app.util.TelegramShareHelper.getInstalledTelegramPackage(context)
        val (intent, uris) = com.tastydrive.app.util.TelegramShareHelper.buildShareIntent(context, selectedItems, pkg)
        com.tastydrive.app.util.TelegramShareHelper.grantUriPermissions(context, intent, uris, pkg)
        return intent
    }

    fun getNativeEngineInfo(): String {
        return if (NativeEngine.isNativeReady()) {
            try {
                NativeEngine.initNative()
            } catch (e: Exception) {
                "Native C Core Active (SIMD Accelerated)"
            }
        } else {
            "Kotlin Engine Fallback (Native Library Not Loaded)"
        }
    }
}
