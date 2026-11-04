package com.tastydrive.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.tastydrive.app.data.FileCategory
import com.tastydrive.app.data.MediaItem
import java.io.File

object TelegramShareHelper {

    // Common package names for Telegram and Telegram-compatible clients
    val TELEGRAM_PACKAGES = listOf(
        "org.telegram.messenger",        // Official Play Store Telegram
        "org.telegram.messenger.web",    // Official Direct APK (from telegram.org)
        "org.telegram.messenger.beta",   // Official Beta
        "org.thunderdog.challegram",      // Telegram X
        "org.telegram.plus",             // Plus Messenger
        "org.nekogram.nekogram",         // Nekogram
        "nekox.messenger",               // NekoX
        "org.telegram.BApplication"      // BGram
    )

    /**
     * Maximum number of large files to share in a single intent.
     * Prevents TransactionTooLargeException, Android IPC buffer exhaustion,
     * and Telegram ShareActivity OutOfMemoryError / crashes.
     */
    const val MAX_SHARE_BATCH_SIZE = 25

    /**
     * Checks if any Telegram client is installed on the device.
     */
    fun getInstalledTelegramPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in TELEGRAM_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (e: Exception) {
                // Not installed under this package name
            }
        }
        return null
    }

    /**
     * Converts a MediaItem into a grantable FileProvider URI.
     * Guarantees that third-party apps like Telegram have full read access
     * without triggering SecurityException on Android 9 - 15+.
     */
    fun getShareableUri(context: Context, item: MediaItem): Uri {
        val authority = "${context.packageName}.fileprovider"

        // 1. Direct path check
        if (!item.path.isNullOrEmpty()) {
            val file = File(item.path)
            if (file.exists() && file.canRead()) {
                try {
                    return FileProvider.getUriForFile(context, authority, file)
                } catch (e: Exception) {
                    try {
                        return FileProvider.getUriForFile(context, authority, file.canonicalFile)
                    } catch (ex: Exception) {
                        // ignore and try fallback
                    }
                }
            }
        }

        // 2. Query real DATA column if item has a content:// URI from MediaStore
        if (item.uri.scheme == "content" && item.uri.authority != authority) {
            try {
                val projection = arrayOf(MediaStore.MediaColumns.DATA)
                context.contentResolver.query(item.uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (pathCol != -1) {
                            val resolvedPath = cursor.getString(pathCol)
                            if (!resolvedPath.isNullOrEmpty()) {
                                val file = File(resolvedPath)
                                if (file.exists() && file.canRead()) {
                                    return FileProvider.getUriForFile(context, authority, file)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // 3. Fallback to existing URI
        return item.uri
    }

    /**
     * Builds an ACTION_SEND or ACTION_SEND_MULTIPLE intent with FileProvider URIs,
     * ClipData, and proper flags.
     */
    fun buildShareIntent(
        context: Context,
        items: List<MediaItem>,
        targetPackage: String? = null
    ): Pair<Intent, List<Uri>> {
        val validItems = items.filter { item ->
            if (!item.path.isNullOrEmpty()) {
                val f = File(item.path)
                f.exists() && f.length() > 0
            } else {
                true
            }
        }

        if (validItems.isEmpty()) {
            throw IllegalArgumentException("Selected files no longer exist on storage.")
        }

        val uris = ArrayList<Uri>(validItems.size)
        for (item in validItems) {
            uris.add(getShareableUri(context, item))
        }

        // Determine proper MIME type
        val mimeType = when {
            validItems.size == 1 -> {
                val item = validItems[0]
                when {
                    item.displayName.endsWith(".apk", ignoreCase = true) -> "application/vnd.android.package-archive"
                    item.displayName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                    item.category == FileCategory.VIDEOS -> "video/*"
                    item.category == FileCategory.PHOTOS -> "image/*"
                    item.category == FileCategory.AUDIO -> "audio/*"
                    else -> item.mimeType.ifEmpty { "*/*" }
                }
            }
            validItems.all { it.category == FileCategory.VIDEOS } -> "video/*"
            validItems.all { it.category == FileCategory.PHOTOS } -> "image/*"
            validItems.all { it.category == FileCategory.AUDIO } -> "audio/*"
            validItems.all { it.category == FileCategory.PDFS } -> "application/pdf"
            validItems.all { it.category == FileCategory.APKS } -> "application/vnd.android.package-archive"
            else -> "*/*"
        }

        val intent = Intent().apply {
            if (uris.size == 1) {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uris[0])
                type = mimeType
            } else {
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                type = mimeType
            }

            // CRITICAL: Read permission flags
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // CRITICAL FOR ANDROID 7+: Set ClipData so OS grants permission to all URIs
            val clipData = ClipData.newUri(context.contentResolver, "Large Files", uris[0])
            for (i in 1 until uris.size) {
                clipData.addItem(ClipData.Item(uris[i]))
            }
            this.clipData = clipData

            if (!targetPackage.isNullOrEmpty()) {
                setPackage(targetPackage)
            }
        }

        return Pair(intent, uris)
    }

    /**
     * Explicitly grants URI read permissions to target package and known Telegram clients.
     */
    fun grantUriPermissions(
        context: Context,
        intent: Intent,
        uris: List<Uri>,
        targetPackage: String? = null
    ) {
        // Target specific package if given
        if (!targetPackage.isNullOrEmpty()) {
            for (uri in uris) {
                try {
                    context.grantUriPermission(
                        targetPackage,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        // Grant to all installed Telegram clients on device
        for (pkg in TELEGRAM_PACKAGES) {
            for (uri in uris) {
                try {
                    context.grantUriPermission(
                        pkg,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        // Also query matching intent activities and grant
        try {
            val resolved = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (info in resolved) {
                val pkg = info.activityInfo.packageName
                for (uri in uris) {
                    try {
                        context.grantUriPermission(
                            pkg,
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
