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
import com.tastydrive.app.databinding.FragmentLargeFilesBinding
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

            shareToTelegram(selected)
        }
    }

    private fun updateSelectionState(selectedCount: Int) {
        val totalLarge = adapter.currentList.size
        binding.tvSelectedStats.text = "$selectedCount of $totalLarge selected"
        binding.btnShareToTelegram.text = "🚀 Share $selectedCount Files to Telegram App"
        binding.btnShareToTelegram.isEnabled = selectedCount > 0
    }

    private fun shareToTelegram(items: List<com.tastydrive.app.data.MediaItem>) {
        val shareIntent = viewModel.createTelegramShareIntent(items)
        try {
            startActivity(shareIntent)
        } catch (e: ActivityNotFoundException) {
            // Telegram app not installed with official package name; fallback to system chooser
            val chooser = Intent.createChooser(
                shareIntent.apply { setPackage(null) },
                "Share Large Files to Telegram"
            )
            try {
                startActivity(chooser)
            } catch (ex: Exception) {
                Toast.makeText(requireContext(), "Could not open share chooser: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
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

