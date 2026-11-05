package com.tastydrive.app.service

import android.content.Context
import android.util.Log
import com.tastydrive.app.data.AppPreferences
import com.tastydrive.app.data.MediaItem
import com.tastydrive.app.data.UploadStats
import com.tastydrive.app.data.UploadStatus
import com.tastydrive.app.util.LocalServerDiscovery
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class ApiEndpointType {
    CLOUD,
    LOCAL
}

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

    // Local Docker API Live Status
    private val _isLocalConnected = MutableStateFlow(false)
    val isLocalConnected: StateFlow<Boolean> = _isLocalConnected.asStateFlow()

    private val _localServerUrl = MutableStateFlow(prefs.localServerUrl)
    val localServerUrl: StateFlow<String> = _localServerUrl.asStateFlow()

    private val _localServerStatusText = MutableStateFlow("Local Docker: Offline")
    val localServerStatusText: StateFlow<String> = _localServerStatusText.asStateFlow()

    private var masterUploadJob: Job? = null
    private var speedCalculationJob: Job? = null
    private var localHealthJob: Job? = null

    // Real-time speed & stats tracker
    private val bytesInLastSecond = AtomicLong(0L)
    private val totalBytesUploaded = AtomicLong(0L)
    private val roundRobinCounter = AtomicInteger(0)
    private var lastPublishTime = 0L

    init {
        startSpeedMonitor()
        startLocalServerHealthMonitor()
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
        if (item.isOver2GB) return
        item.status = UploadStatus.QUEUED
        allMediaMap[item.id] = item
        publishItems()
        recalculateStats()
        startUploadProcess()
    }

    fun startUploadProcess() {
        if (!prefs.isConfigured()) {
            Log.w(TAG, "Cannot start upload: Neither Cloud nor Local Telegram API is configured!")
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

                val hasCloud = prefs.hasCloudApi()
                val hasLocal = prefs.hasLocalApi() && _isLocalConnected.value

                if (!hasCloud && !hasLocal) {
                    delay(2000)
                    continue
                }

                // 1. Acquire worker slot first
                semaphore.acquire()

                if (_isPaused.value || !_isUploading.value) {
                    semaphore.release()
                    break
                }

                // 2. Atomically select next eligible queued item and decide API route
                val nextPair = synchronized(allMediaMap) {
                    val candidate = allMediaMap.values.firstOrNull { item ->
                        if (item.status != UploadStatus.QUEUED || item.isOver2GB) return@firstOrNull false

                        if (item.isOver50MB) {
                            // > 50MB files REQUIRE Local Docker API server
                            hasLocal
                        } else {
                            // <= 50MB files can use Cloud OR Local API
                            hasCloud || hasLocal
                        }
                    }

                    candidate?.let { item ->
                        val endpoint = when {
                            item.isOver50MB -> ApiEndpointType.LOCAL
                            hasCloud && hasLocal -> {
                                // Balance load across both Cloud and Local APIs
                                if (roundRobinCounter.incrementAndGet() % 2 == 0) ApiEndpointType.LOCAL else ApiEndpointType.CLOUD
                            }
                            hasLocal -> ApiEndpointType.LOCAL
                            else -> ApiEndpointType.CLOUD
                        }

                        item.status = UploadStatus.UPLOADING
                        item.progress = 0
                        item.uploadTarget = if (endpoint == ApiEndpointType.LOCAL) "Local Docker 💻" else "Cloud Bot ☁️"
                        Pair(item, endpoint)
                    }
                }

                if (nextPair == null) {
                    semaphore.release()
                    val running = synchronized(allMediaMap) {
                        allMediaMap.values.count { it.status == UploadStatus.UPLOADING }
                    }
                    if (running == 0) {
                        val waitingForLocal = synchronized(allMediaMap) {
                            allMediaMap.values.count { it.status == UploadStatus.QUEUED && it.isOver50MB }
                        }
                        if (waitingForLocal > 0 && !hasLocal) {
                            Log.i(TAG, "$waitingForLocal files >50MB are waiting in queue for Local Docker API connection.")
                            delay(3000)
                            continue
                        } else {
                            Log.i(TAG, "All eligible items in queue processed!")
                            _isUploading.value = false
                            break
                        }
                    } else {
                        delay(1000)
                        continue
                    }
                }

                val (itemToUpload, endpoint) = nextPair

                // Publish throttled so UI sees this item as UPLOADING
                publishItemsThrottled()
                recalculateStats()

                // 3. Launch dedicated concurrent worker
                val job = launch {
                    try {
                        processUpload(itemToUpload, endpoint)
                    } finally {
                        semaphore.release()
                        activeJobMap.remove(itemToUpload.id)
                    }
                }
                activeJobMap[itemToUpload.id] = job
            }

            _isUploading.value = false
            recalculateStats()
        }
    }

    private suspend fun processUpload(item: MediaItem, endpoint: ApiEndpointType) {
        var lastReportedBytes = 0L

        val isLocal = (endpoint == ApiEndpointType.LOCAL)
        val token = if (isLocal) prefs.getEffectiveLocalBotToken() else prefs.cloudBotToken
        val channelId = if (isLocal) prefs.getEffectiveLocalChannelId() else prefs.cloudChannelId
        val baseUrl = if (isLocal) prefs.localServerUrl else TelegramApiClient.CLOUD_API_BASE

        val result = apiClient.uploadMedia(
            token = token,
            channelId = channelId,
            item = item,
            baseUrl = baseUrl,
            isLocalApi = isLocal,
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

    // --- Local Server Management & Background Discovery ---

    private fun startLocalServerHealthMonitor() {
        localHealthJob?.cancel()
        localHealthJob = scope.launch {
            while (true) {
                checkLocalServerHealth()
                delay(8000)
            }
        }
    }

    suspend fun checkLocalServerHealth(): Boolean {
        if (!prefs.isLocalApiEnabled || prefs.localServerUrl.isBlank()) {
            _isLocalConnected.value = false
            _localServerStatusText.value = "Local API: Not Configured"
            return false
        }

        val url = prefs.localServerUrl
        val token = prefs.getEffectiveLocalBotToken()
        val isUp = LocalServerDiscovery.pingServer(url, token)

        val previous = _isLocalConnected.value
        _isLocalConnected.value = isUp
        _localServerUrl.value = url

        _localServerStatusText.value = if (isUp) {
            "Local Docker: Connected (${url.removePrefix("http://").removePrefix("https://")}) 🚀"
        } else {
            "Local Docker: Offline (${url.removePrefix("http://").removePrefix("https://")})"
        }

        // If local server just came online and we are uploading or have items waiting, trigger upload process!
        if (!previous && isUp && _isUploading.value) {
            Log.i(TAG, "Local Docker API reconnected! Resuming queue for >50MB items...")
            startUploadProcess()
        }

        return isUp
    }

    suspend fun autoDiscoverAndConnectLocal(
        onProgress: ((checked: Int, total: Int) -> Unit)? = null
    ): LocalServerDiscovery.DiscoveredServer? {
        val found = LocalServerDiscovery.discoverServer(
            preferredIp = prefs.lastDiscoveredIp.ifBlank { null },
            targetPort = LocalServerDiscovery.DEFAULT_PORT,
            botToken = prefs.getEffectiveLocalBotToken().ifBlank { null },
            onProgress = onProgress
        )

        if (found != null) {
            prefs.localServerUrl = found.url
            prefs.lastDiscoveredIp = found.ip
            _localServerUrl.value = found.url
            _isLocalConnected.value = true
            _localServerStatusText.value = "Local Docker: Connected (${found.ip}:${found.port}) 🚀"
            recalculateStats()

            // If uploads are running or have pending files, trigger upload!
            if (_isUploading.value) {
                startUploadProcess()
            }
        }

        return found
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
                    if (item.isOver50MB) oversized++
                    pending++
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
