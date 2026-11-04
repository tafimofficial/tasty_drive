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
            if (item.status == UploadStatus.FAILED || item.status == UploadStatus.PAUSED) {
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
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.stats.collect { stats ->
                        binding.tvCountUploaded.text = stats.completedCount.toString()
                        binding.tvCountRemaining.text = stats.pendingCount.toString()
                        binding.tvCountLarge.text = stats.oversizedCount.toString()
                        binding.tvCountFailed.text = stats.failedCount.toString()

                        binding.tvLiveSpeed.text = if (stats.isUploading) "🚀 ${stats.currentSpeedStr}" else "💤 Idle"
                        binding.pbOverall.progress = stats.progressPercent

                        if (stats.failedCount > 0) {
                            binding.btnRetryFailed.visibility = View.VISIBLE
                        } else {
                            binding.btnRetryFailed.visibility = View.GONE
                        }

                        if (stats.isPaused) {
                            binding.btnStartResume.text = "▶ Resume Uploads"
                        } else if (stats.isUploading) {
                            binding.btnStartResume.text = "⚡ Uploading..."
                        } else {
                            binding.btnStartResume.text = "🚀 Start Uploads"
                        }
                    }
                }

                launch {
                    viewModel.queueItems.collect { items ->
                        adapter.submitList(items)
                        val totalPending = viewModel.stats.value.pendingCount
                        if (totalPending > items.size) {
                            binding.tvQueueTitle.text = "Active Queue (Next ${items.size} of $totalPending pending)"
                        } else {
                            binding.tvQueueTitle.text = "Active Queue & Recent (${items.size})"
                        }
                        binding.layoutEmptyQueue.visibility = if (items.isEmpty() && totalPending == 0) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.isScanning.collect { isScanning ->
                        binding.swipeRefresh.isRefreshing = isScanning
                    }
                }

                launch {
                    val networkDesc = NetworkHelper.getInstance(requireContext()).getNetworkStatusDescription()
                    binding.tvNetworkInfo.text = "Network: $networkDesc"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
