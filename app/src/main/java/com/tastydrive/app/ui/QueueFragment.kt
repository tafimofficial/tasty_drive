package com.tastydrive.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.tastydrive.app.R
import com.tastydrive.app.data.UploadStatus
import com.tastydrive.app.databinding.FragmentQueueBinding
import com.tastydrive.app.util.NetworkHelper
import kotlinx.coroutines.launch

class QueueFragment : Fragment() {
    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: QueueAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = QueueAdapter { item ->
            if (item.status == UploadStatus.FAILED) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Failed File: ${item.displayName}")
                    .setMessage(item.errorMessage ?: "This file could not be read or uploaded.")
                    .setPositiveButton("🔄 Retry") { _, _ ->
                        viewModel.enqueueSingleItem(item)
                        Toast.makeText(requireContext(), "Retrying ${item.displayName}", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("🗑️ Clear / Ignore") { _, _ ->
                        viewModel.clearSingleFailed(item)
                        Toast.makeText(requireContext(), "Ignored ${item.displayName}", Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton("Cancel", null)
                    .show()
            } else if (item.status == UploadStatus.PAUSED) {
                viewModel.enqueueSingleItem(item)
                Toast.makeText(requireContext(), "Queued ${item.displayName}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvQueue.layoutManager = LinearLayoutManager(requireContext())
        binding.rvQueue.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary, R.color.secondary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.scanMedia()
        }

        binding.btnStartResume.setOnClickListener {
            if (!viewModel.prefs.isConfigured()) {
                Toast.makeText(
                    requireContext(),
                    "Please set your Telegram Bot Token and Channel ID in Settings first!",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            viewModel.startOrResumeUploads()
        }

        binding.btnPause.setOnClickListener {
            viewModel.pauseUploads()
        }

        binding.btnRetryFailed.setOnClickListener {
            viewModel.retryFailed()
        }

        binding.btnClearFailed.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Clear All Failed Files?")
                .setMessage("This will remove all inaccessible or corrupt files (such as restricted WhatsApp databases or deleted cache) from the failed queue.")
                .setPositiveButton("Clear All") { _, _ ->
                    viewModel.clearFailed()
                    Toast.makeText(requireContext(), "All failed items cleared!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.stats.collect { stats ->
                        _binding?.let { b ->
                            b.tvCountUploaded.text = stats.completedCount.toString()
                            b.tvCountRemaining.text = stats.pendingCount.toString()
                            b.tvCountLarge.text = stats.oversizedCount.toString()
                            b.tvCountFailed.text = stats.failedCount.toString()

                            b.tvLiveSpeed.text = if (stats.isUploading) "🚀 ${stats.currentSpeedStr}" else "💤 Idle"
                            b.pbOverall.progress = stats.progressPercent

                            b.layoutFailedActions.visibility = if (stats.failedCount > 0) View.VISIBLE else View.GONE

                            if (stats.isPaused) {
                                b.btnStartResume.text = "▶ Resume Uploads"
                            } else if (stats.isUploading) {
                                b.btnStartResume.text = "⚡ Uploading..."
                            } else {
                                b.btnStartResume.text = "🚀 Start Uploads"
                            }
                        }
                    }
                }

                launch {
                    viewModel.queueItems.collect { items ->
                        _binding?.let { b ->
                            adapter.submitList(items)
                            val totalPending = viewModel.stats.value.pendingCount
                            if (totalPending > items.size) {
                                b.tvQueueTitle.text = "Active Queue (Next ${items.size} of $totalPending pending)"
                            } else {
                                b.tvQueueTitle.text = "Active Queue & Recent (${items.size})"
                            }
                            b.layoutEmptyQueue.visibility = if (items.isEmpty() && totalPending == 0) View.VISIBLE else View.GONE
                        }
                    }
                }

                launch {
                    viewModel.isScanning.collect { isScanning ->
                        _binding?.let { b ->
                            b.swipeRefresh.isRefreshing = isScanning
                        }
                    }
                }

                launch {
                    viewModel.localServerStatusText.collect { localStatus ->
                        _binding?.let { b ->
                            val ctx = context ?: return@collect
                            val networkDesc = NetworkHelper.getInstance(ctx).getNetworkStatusDescription()
                            b.tvNetworkInfo.text = "Network: $networkDesc • $localStatus"
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
