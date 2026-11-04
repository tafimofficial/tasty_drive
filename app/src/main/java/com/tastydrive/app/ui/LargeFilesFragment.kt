package com.tastydrive.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
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
import com.tastydrive.app.data.MediaItem
import com.tastydrive.app.databinding.FragmentLargeFilesBinding
import com.tastydrive.app.util.TelegramShareHelper
import kotlinx.coroutines.launch

class LargeFilesFragment : Fragment() {
    private var _binding: FragmentLargeFilesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: LargeFilesAdapter

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
        setupListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = LargeFilesAdapter(
            onSelectionChanged = { selectedCount ->
                updateSelectionState(selectedCount)
            },
            onItemClick = { item ->
                // Handled in adapter
            }
        )
        binding.rvLargeFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLargeFiles.adapter = adapter
    }

    private fun setupListeners() {
        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            adapter.selectAll(isChecked)
        }

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

    private fun updateSelectionState(selectedCount: Int) {
        val totalLarge = adapter.currentList.size
        binding.tvSelectedStats.text = "$selectedCount of $totalLarge selected"
        binding.btnShareToTelegram.text = if (selectedCount > 0) {
            "🚀 Share $selectedCount Files to Telegram"
        } else {
            "🚀 Share Files to Telegram"
        }
        binding.btnShareToTelegram.isEnabled = selectedCount > 0
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

            // If we successfully triggered the batch, uncheck shared items so user can send remaining easily
            if (isBatchLimited) {
                batchToShare.forEach { it.isSelected = false }
                adapter.notifyDataSetChanged()
                updateSelectionState(adapter.getSelectedItems().size)
            }

        } catch (e: ActivityNotFoundException) {
            // Telegram package not found or direct activity missing; fallback to system chooser
            launchSystemChooser(batchToShare)
        } catch (ex: Exception) {
            ex.printStackTrace()
            // Final fallback to system chooser
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
                viewModel.largeFiles.collect { files ->
                    adapter.submitList(files)
                    binding.tvLargeCountBadge.text = "${files.size} Files"
                    binding.layoutEmptyLarge.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvLargeFiles.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
                    updateSelectionState(adapter.getSelectedItems().size)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
