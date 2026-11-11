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
import androidx.recyclerview.widget.GridLayoutManager
import com.tastydrive.app.data.MediaItem
import com.tastydrive.app.data.UploadStatus
import com.tastydrive.app.databinding.FragmentGalleryBinding
import kotlinx.coroutines.launch

class GalleryFragment : Fragment() {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: GalleryAdapter

    private var currentDisplayedItems: List<MediaItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilters()
        setupSelectionBar()
        setupActionButtons()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = GalleryAdapter(
            onSelectionChanged = { selectedCount ->
                updateSelectionState(selectedCount)
            },
            onItemClick = { item ->
                if (item.status == UploadStatus.COMPLETED) {
                    Toast.makeText(requireContext(), "✔ Already Uploaded: ${item.displayName}", Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.rvGallery.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvGallery.adapter = adapter
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener { switchFilter(GalleryFilter.ALL) }
        binding.chipApks.setOnClickListener { switchFilter(GalleryFilter.APKS) }
        binding.chipPdfs.setOnClickListener { switchFilter(GalleryFilter.PDFS) }
        binding.chipPhotos.setOnClickListener { switchFilter(GalleryFilter.PHOTOS) }
        binding.chipVideos.setOnClickListener { switchFilter(GalleryFilter.VIDEOS) }
        binding.chipDocs.setOnClickListener { switchFilter(GalleryFilter.DOCS) }
        binding.chipAudio.setOnClickListener { switchFilter(GalleryFilter.AUDIO) }
        binding.chipPending.setOnClickListener { switchFilter(GalleryFilter.PENDING) }
        binding.chipUploaded.setOnClickListener { switchFilter(GalleryFilter.UPLOADED) }
    }

    private fun switchFilter(filter: GalleryFilter) {
        adapter.clearSelection()
        _binding?.cbSelectAllGallery?.isChecked = false
        updateSelectionState(0)
        viewModel.setGalleryFilter(filter)
    }

    private fun setupSelectionBar() {
        binding.cbSelectAllGallery.setOnCheckedChangeListener { _, isChecked ->
            adapter.selectAll(isChecked)
        }

        binding.btnClearSelection.setOnClickListener {
            adapter.clearSelection()
            binding.cbSelectAllGallery.isChecked = false
            updateSelectionState(0)
        }
    }

    private fun setupActionButtons() {
        // --- Option 1: Upload Only Selected Files ---
        binding.btnUploadSelected.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Please tap files to select them, or use 'Backup All Unsent'!",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (!viewModel.prefs.isConfigured()) {
                Toast.makeText(
                    requireContext(),
                    "Please configure Telegram Bot Token and Channel ID in Settings!",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            for (item in selected) {
                viewModel.enqueueSingleItem(item)
            }
            viewModel.startOrResumeUploads()

            Toast.makeText(
                requireContext(),
                "🚀 Started uploading ${selected.size} selected files!",
                Toast.LENGTH_SHORT
            ).show()

            adapter.clearSelection()
            binding.cbSelectAllGallery.isChecked = false
            updateSelectionState(0)
        }

        // --- Option 2: Backup All Unsent Media ---
        binding.btnBackupAll.setOnClickListener {
            if (!viewModel.prefs.isConfigured()) {
                Toast.makeText(
                    requireContext(),
                    "Please configure Telegram Bot Token and Channel ID in Settings!",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            viewModel.startOrResumeUploads()
            Toast.makeText(
                requireContext(),
                "🚀 Started backing up all unsent media!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateSelectionState(selectedCount: Int) {
        val b = _binding ?: return
        val unsentCount = currentDisplayedItems.count { it.status != UploadStatus.COMPLETED }

        b.tvGallerySelectedCount.text = "$selectedCount of $unsentCount selected"
        b.btnClearSelection.visibility = if (selectedCount > 0) View.VISIBLE else View.GONE

        b.btnUploadSelected.text = if (selectedCount > 0) {
            "📤 Upload Selected ($selectedCount)"
        } else {
            "📤 Upload Selected"
        }

        b.cbSelectAllGallery.setOnCheckedChangeListener(null)
        b.cbSelectAllGallery.isChecked = (unsentCount > 0 && selectedCount == unsentCount)
        b.cbSelectAllGallery.isEnabled = unsentCount > 0
        b.cbSelectAllGallery.setOnCheckedChangeListener { _, isChecked ->
            adapter.selectAll(isChecked)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.galleryItems.collect { items ->
                    currentDisplayedItems = items
                    _binding?.let {
                        adapter.submitList(items)
                        val selectedCount = adapter.getSelectedItems().size
                        updateSelectionState(selectedCount)
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
