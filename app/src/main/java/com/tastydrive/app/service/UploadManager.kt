package com.tastydrive.app.service

import android.content.Context
import android.util.Log
import com.tastydrive.app.data.AppPreferences
import com.tastydrive.app.data.MediaItem
import com.tastydrive.app.data.UploadStats
import com.tastydrive.app.data.UploadStatus
import com.tastydrive.app.util.NativeEngine
import com.tastydrive.app.util.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class UploadManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = AppPreferences.getInstance(context)
    private val apiClient = TelegramApiClient(context)
    private val networkHelper = NetworkHelper.getInstance(context)

    // In-memory queues and items
    private val allMediaMap = ConcurrentHashMap<Long, MediaItem>()
    private val activeJobMap = ConcurrentHashMap<Long, Job>()

    private val _stats = MutableStateFlow(UploadStats())
    val stats: StateFlow<UploadStats> = _stats.asStateFlow()

    private val _itemsFlow = MutableStateFlow<List<MediaItem>>(emptyList())
    val itemsFlow: StateFlow<List<MediaItem>> = _itemsFlow.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private var masterUploadJob: Job? = null
    private var speedCalculationJob: Job? = null

    // Real-time speed tracker
    private val bytesInLastSecond = AtomicLong(0L)
    private val totalBytesUploaded = AtomicLong(0L)
    private var lastPublishTime = 0L

    init {
        startSpeedMonitor()
    }

    fun setAllMedia(items: List<MediaItem>) {
        allMediaMap.clear()
        for (item in items) {
            allMediaMap[item.id] = item
        }
        publishItems()
        recalculateStats()
    }

    fun enqueueAllPending() {
        val pending = allMediaMap.values.filter {
            it.status == UploadStatus.QUEUED || it.status == UploadStatus.PAUSED || it.status == UploadStatus.FAILED
        }
        if (pending.isNotEmpty()) {
            for (p in pending) {
                p.status = UploadStatus.QUEUED
                p.errorMessage = null
            }
            publishItems()
            recalculateStats()
            startUploadProcess()
        }
    }

    fun enqueueSingle(item: MediaItem) {
        if (item.isOver50MB) return
        item.status = UploadStatus.QUEUED
        allMediaMap[item.id] = item
        publishItems()
        recalculateStats()
        startUploadProcess()
    }

    fun startUploadProcess() {
        if (!prefs.isConfigured()) {
            Log.w(TAG, "Cannot start upload: Telegram bot token or channel ID not configured!")
            return
        }

        _isPaused.value = false
        _isUploading.value = true

        UploadForegroundService.startService(context)

        if (masterUploadJob?.isActive == true) return

        masterUploadJob = scope.launch {
            val concurrency = prefs.concurrency.coerceIn(1, 4)
            val semaphore = Semaphore(concurrency)

            while (_isUploading.value && !_isPaused.value) {
                // Check network constraints
                val pref = prefs.networkPreference
                if (!networkHelper.isUploadAllowed(pref)) {
                    delay(2000)
                    continue
                }

                // 1. Acquire worker slot first so master loop never pulls more items than available slots!
                semaphore.acquire()

                if (_isPaused.value || !_isUploading.value) {
                    semaphore.release()
                    break
                }

                // 2. Atomically pull next queued item and mark it UPLOADING immediately!
                val nextItem = synchronized(allMediaMap) {
                    val candidate = allMediaMap.values.firstOrNull {
                        it.status == UploadStatus.QUEUED && !it.isOver50MB
                    }
                    candidate?.also {
                        it.status = UploadStatus.UPLOADING
                        it.progress = 0
                    }
                }

                if (nextItem == null) {
                    semaphore.release()
                    val running = synchronized(allMediaMap) {
                        allMediaMap.values.count { it.status == UploadStatus.UPLOADING }
                    }
                    if (running == 0) {
                        Log.i(TAG, "All eligible items in queue processed!")
                        _isUploading.value = false
                        break
                    } else {
                        delay(1000)
                        continue
                    }
                }

                // Publish throttled so UI sees this item as UPLOADING
                publishItemsThrottled()
                recalculateStats()

                // 3. Launch dedicated concurrent worker for nextItem
                val job = launch {
                    try {
                        processUpload(nextItem)
                    } finally {
                        semaphore.release()
                        activeJobMap.remove(nextItem.id)
                    }
                }
                activeJobMap[nextItem.id] = job
            }

            _isUploading.value = false
            recalculateStats()
        }
    }

    private suspend fun processUpload(item: MediaItem) {
        var lastReportedBytes = 0L

        val result = apiClient.uploadMedia(
            token = prefs.botToken,
            channelId = prefs.channelId,
            item = item,
            onProgress = { percent, bytesWritten, _ ->
                val delta = bytesWritten - lastReportedBytes
                if (delta > 0) {
                    bytesInLastSecond.addAndGet(delta)
                    totalBytesUploaded.addAndGet(delta)
                    lastReportedBytes = bytesWritten
                }
                item.progress = percent
                publishItemsThrottled(force = (percent == 100))
            }
        )

        when (result) {
            is ApiResult.Success -> {
                item.status = UploadStatus.COMPLETED
                item.progress = 100
                item.errorMessage = null
                prefs.markUploaded(item.id, item.displayName, item.size)
            }
            is ApiResult.Error -> {
                if (_isPaused.value) {
                    item.status = UploadStatus.PAUSED
                } else {
                    item.status = UploadStatus.FAILED
                    item.errorMessage = result.message
                }
            }
        }

        publishItems()
        recalculateStats()
    }

    fun pauseUploads() {
        _isPaused.value = true
        _isUploading.value = false

        for ((id, job) in activeJobMap) {
            job.cancel()
            val item = allMediaMap[id]
            if (item != null && item.status == UploadStatus.UPLOADING) {
                item.status = UploadStatus.PAUSED
            }
        }
        activeJobMap.clear()
        masterUploadJob?.cancel()
        masterUploadJob = null

        publishItems()
        recalculateStats()
    }

    fun resumeUploads() {
        for (item in allMediaMap.values) {
            if (item.status == UploadStatus.PAUSED) {
                item.status = UploadStatus.QUEUED
            }
        }
        publishItems()
        recalculateStats()
        startUploadProcess()
    }

    fun stopUploads() {
        pauseUploads()
        UploadForegroundService.stopService(context)
    }

    fun retryFailed() {
        for (item in allMediaMap.values) {
            if (item.status == UploadStatus.FAILED) {
                item.status = UploadStatus.QUEUED
                item.errorMessage = null
            }
        }
        publishItems()
        recalculateStats()
        startUploadProcess()
    }

    private fun startSpeedMonitor() {
        speedCalculationJob?.cancel()
        speedCalculationJob = scope.launch {
            while (true) {
                delay(1000)
                val bytes = bytesInLastSecond.getAndSet(0L)
                val speedText = NativeEngine.safeFormatSpeed(bytes)
                val current = _stats.value
                _stats.value = current.copy(
                    currentSpeedStr = if (_isUploading.value) speedText else "0 B/s"
                )
            }
        }
    }

    private fun publishItems() {
        _itemsFlow.value = allMediaMap.values.toList()
    }

    private fun publishItemsThrottled(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastPublishTime >= 400L) {
            lastPublishTime = now
            publishItems()
        }
    }

    fun recalculateStats() {
        val items = allMediaMap.values
        val total = items.size
        var completed = 0
        var oversized = 0
        var failed = 0
        var pending = 0
        var totalBytes = 0L

        for (item in items) {
            totalBytes += item.size
            when (item.status) {
                UploadStatus.COMPLETED -> completed++
                UploadStatus.OVERSIZED -> oversized++
                UploadStatus.FAILED -> failed++
                UploadStatus.QUEUED, UploadStatus.UPLOADING, UploadStatus.PAUSED -> {
                    if (item.isOver50MB) oversized++ else pending++
                }
            }
        }

        _stats.value = _stats.value.copy(
            totalCount = total,
            completedCount = completed,
            pendingCount = pending,
            oversizedCount = oversized,
            failedCount = failed,
            totalBytes = totalBytes,
            uploadedBytes = totalBytesUploaded.get(),
            isUploading = _isUploading.value,
            isPaused = _isPaused.value
        )
    }

    fun getCurrentlyUploadingItem(): MediaItem? {
        return allMediaMap.values.firstOrNull { it.status == UploadStatus.UPLOADING }
    }

    companion object {
        private const val TAG = "UploadManager"

        @Volatile
        private var instance: UploadManager? = null

        fun getInstance(context: Context): UploadManager {
            return instance ?: synchronized(this) {
                instance ?: UploadManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
