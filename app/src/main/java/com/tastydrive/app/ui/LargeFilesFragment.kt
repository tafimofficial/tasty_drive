package com.tastydrive.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.tastydrive.app.R
import com.tastydrive.app.data.MediaItem
import com.tastydrive.app.data.UploadStatus
import com.tastydrive.app.databinding.FragmentLargeFilesBinding
import com.tastydrive.app.util.TelegramShareHelper
import kotlinx.coroutines.launch

enum class LargeFilesFilter {
    PENDING,
    ALL,
    UPLOADED
}

class LargeFilesFragment : Fragment() {
    private var _binding: FragmentLargeFilesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: LargeFilesAdapter

    private var currentFilter = LargeFilesFilter.PENDING
    private var allLargeFilesList: List<MediaItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLargeFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilters()
        setupListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = LargeFilesAdapter(
            onSelectionChanged = { selectedCount ->
                updateSelectionState(selectedCount)
            },
            onItemClick = { item ->
                // Click handled in adapter
            }
        )
        binding.rvLargeFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLargeFiles.adapter = adapter
    }

    private fun setupFilters() {
        binding.chipLargePending.setOnClickListener {
            currentFilter = LargeFilesFilter.PENDING
            applyCurrentFilter()
        }
        binding.chipLargeAll.setOnClickListener {
            currentFilter = LargeFilesFilter.ALL
            applyCurrentFilter()
        }
        binding.chipLargeUploaded.setOnClickListener {
            currentFilter = LargeFilesFilter.UPLOADED
            applyCurrentFilter()
        }
    }

    private fun setupListeners() {
        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            adapter.selectAll(isChecked)
        }

        // --- Upload via Local Docker Server Button ---
        binding.btnUploadViaLocalDocker.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) {
                Toast.makeText(requireContext(), "Please select at least one large file to upload!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            for (item in selected) {
                viewModel.enqueueSingleItem(item)
            }
            viewModel.startOrResumeUploads()

            Toast.makeText(
                requireContext(),
                "Enqueued ${selected.size} large files to Local Docker upload! 🚀",
                Toast.LENGTH_LONG
            ).show()

            adapter.selectAll(false)
            binding.cbSelectAll.isChecked = false
            updateSelectionState(0)
        }

        // --- Share to Telegram App Button ---
        binding.btnShareToTelegram.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) {
                Toast.makeText(requireContext(), "Please select at least one file to share!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            shareToTelegram(selected, preferDirect = true)
        }

        // Long press on share button opens the system chooser popup
        binding.btnShareToTelegram.setOnLongClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isNotEmpty()) {
                shareToTelegram(selected, preferDirect = false)
                true
            } else {
                false
            }
        }
    }

    private fun applyCurrentFilter() {
        val b = _binding ?: return
        val pendingCount = allLargeFilesList.count { it.status != UploadStatus.COMPLETED }
        val uploadedCount = allLargeFilesList.count { it.status == UploadStatus.COMPLETED }
        val totalCount = allLargeFilesList.size

        b.tvLargeCountBadge.text = "$pendingCount Remaining • $uploadedCount Uploaded"
        b.chipLargePending.text = "⏳ Pending ($pendingCount)"
        b.chipLargeAll.text = "📁 All ($totalCount)"
        b.chipLargeUploaded.text = "⚡ Uploaded ($uploadedCount)"

        val filtered = when (currentFilter) {
            LargeFilesFilter.PENDING -> allLargeFilesList.filter { it.status != UploadStatus.COMPLETED }
            LargeFilesFilter.ALL -> allLargeFilesList
            LargeFilesFilter.UPLOADED -> allLargeFilesList.filter { it.status == UploadStatus.COMPLETED }
        }

        adapter.submitList(filtered)

        val isEmpty = filtered.isEmpty()
        b.layoutEmptyLarge.visibility = if (isEmpty) View.VISIBLE else View.GONE
        b.rvLargeFiles.visibility = if (isEmpty) View.GONE else View.VISIBLE

        if (isEmpty) {
            when (currentFilter) {
                LargeFilesFilter.PENDING -> {
                    b.tvEmptyLargeTitle.text = if (uploadedCount > 0) "All Remaining Files Uploaded! 🎉" else "No Large Files Found! 📁"
                    b.tvEmptyLargeSubtitle.text = if (uploadedCount > 0) {
                        "All $uploadedCount large files are uploaded! View them in '⚡ Uploaded' tab."
                    } else {
                        "All your photos and videos are within 50MB and can be uploaded directly."
                    }
                }
                LargeFilesFilter.UPLOADED -> {
                    b.tvEmptyLargeTitle.text = "No Uploaded Large Files Yet ⏳"
                    b.tvEmptyLargeSubtitle.text = "Select files in 'Pending' and upload via Local Docker or Share to Telegram."
                }
                LargeFilesFilter.ALL -> {
                    b.tvEmptyLargeTitle.text = "No Files Over 50MB 📁"
                    b.tvEmptyLargeSubtitle.text = "All your media files are within the 50MB cloud limit."
                }
            }
        }

        b.cbSelectAll.isEnabled = pendingCount > 0 && currentFilter != LargeFilesFilter.UPLOADED
        b.cbSelectAll.text = if (currentFilter == LargeFilesFilter.UPLOADED) {
            "All $uploadedCount Files Uploaded ⚡"
        } else {
            "Select All Remaining ($pendingCount)"
        }

        updateSelectionState(adapter.getSelectedItems().size)
    }

    private fun updateSelectionState(selectedCount: Int) {
        val b = _binding ?: return
        val pendingCount = allLargeFilesList.count { it.status != UploadStatus.COMPLETED }
        b.tvSelectedStats.text = "$selectedCount of $pendingCount selected"

        b.btnUploadViaLocalDocker.text = if (selectedCount > 0) {
            "💻 Upload $selectedCount Files via Local Docker"
        } else {
            "💻 Upload via Local Docker API"
        }
        b.btnUploadViaLocalDocker.isEnabled = selectedCount > 0

        b.btnShareToTelegram.text = if (selectedCount > 0) {
            "🚀 Share $selectedCount Files to Telegram"
        } else {
            "🚀 Share Files to Telegram App"
        }
        b.btnShareToTelegram.isEnabled = selectedCount > 0
    }

    private fun shareToTelegram(items: List<MediaItem>, preferDirect: Boolean) {
        val context = requireContext()
        val maxBatch = TelegramShareHelper.MAX_SHARE_BATCH_SIZE

        // Safety batching to prevent Telegram OutOfMemory / Binder limits
        val isBatchLimited = items.size > maxBatch
        val batchToShare = if (isBatchLimited) items.take(maxBatch) else items

        if (isBatchLimited) {
            val remaining = items.size - maxBatch
            Toast.makeText(
                context,
                "Sharing first $maxBatch files to Telegram (Batch limit to avoid crash). $remaining remaining for next batch! 🚀",
                Toast.LENGTH_LONG
            ).show()
        }

        val installedTelegramPkg = TelegramShareHelper.getInstalledTelegramPackage(context)

        try {
            if (preferDirect && installedTelegramPkg != null) {
                // Direct launch to detected Telegram client
                val (intent, uris) = TelegramShareHelper.buildShareIntent(
                    context,
                    batchToShare,
                    installedTelegramPkg
                )
                TelegramShareHelper.grantUriPermissions(context, intent, uris, installedTelegramPkg)
                startActivity(intent)
            } else {
                // System Chooser popup with Telegram highlighted
                launchSystemChooser(batchToShare)
            }

            // Mark shared items as completed and record in upload history
            for (sharedItem in batchToShare) {
                sharedItem.status = UploadStatus.COMPLETED
                sharedItem.isSelected = false
                viewModel.prefs.markUploaded(sharedItem.id, sharedItem.displayName, sharedItem.size)
            }
            viewModel.uploadManager.recalculateStats()
            applyCurrentFilter()

        } catch (e: ActivityNotFoundException) {
            launchSystemChooser(batchToShare)
        } catch (ex: Exception) {
            ex.printStackTrace()
            try {
                launchSystemChooser(batchToShare)
            } catch (fatal: Exception) {
                Toast.makeText(
                    context,
                    "Could not open share dialog: ${fatal.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun launchSystemChooser(batchToShare: List<MediaItem>) {
        val context = requireContext()
        val (baseIntent, uris) = TelegramShareHelper.buildShareIntent(context, batchToShare, null)
        TelegramShareHelper.grantUriPermissions(context, baseIntent, uris, null)

        val chooser = Intent.createChooser(
            baseIntent,
            "Share ${batchToShare.size} Large Files with Telegram"
        ).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(chooser)
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Collect large files
                launch {
                    viewModel.largeFiles.collect { files ->
                        allLargeFilesList = files
                        applyCurrentFilter()
                    }
                }

                // Collect local docker server status
                launch {
                    viewModel.isLocalConnected.collect { isConnected ->
                        _binding?.let { b ->
                            context?.let { ctx ->
                                if (isConnected) {
                                    b.tvLocalDockerStatusInLarge.setTextColor(
                                        ContextCompat.getColor(ctx, R.color.status_green)
                                    )
                                    b.tvLocalDockerStatusInLarge.text = "💻 Local Docker Server: Connected 🟢 (Auto-Upload Active up to 2GB!)"
                                    b.btnUploadViaLocalDocker.visibility = View.VISIBLE
                                } else {
                                    b.tvLocalDockerStatusInLarge.setTextColor(
                                        ContextCompat.getColor(ctx, R.color.status_orange)
                                    )
                                    b.tvLocalDockerStatusInLarge.text = "💻 Local Docker Server: Offline 🔴 (Queued files wait for PC)"
                                    b.btnUploadViaLocalDocker.visibility = View.GONE
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
