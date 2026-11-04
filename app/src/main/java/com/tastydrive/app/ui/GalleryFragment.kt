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
import com.tastydrive.app.data.UploadStatus
import com.tastydrive.app.databinding.FragmentGalleryBinding
import kotlinx.coroutines.launch

class GalleryFragment : Fragment() {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: GalleryAdapter

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
        setupUploadButton()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = GalleryAdapter { item ->
            if (item.isOver50MB) {
                Toast.makeText(
                    requireContext(),
                    "${item.displayName} is > 50MB. See 'Large Files' tab to share via Telegram!",
                    Toast.LENGTH_LONG
                ).show()
            } else if (item.status != UploadStatus.COMPLETED) {
                viewModel.enqueueSingleItem(item)
                Toast.makeText(requireContext(), "Queued ${item.displayName}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvGallery.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvGallery.adapter = adapter
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener { viewModel.setGalleryFilter(GalleryFilter.ALL) }
        binding.chipApks.setOnClickListener { viewModel.setGalleryFilter(GalleryFilter.APKS) }
        binding.chipPdfs.setOnClickListener { viewModel.setGalleryFilter(GalleryFilter.PDFS) }
        binding.chipPhotos.setOnClickListener { viewModel.setGalleryFilter(GalleryFilter.PHOTOS) }
        binding.chipVideos.setOnClickListener { viewModel.setGalleryFilter(GalleryFilter.VIDEOS) }
        binding.chipDocs.setOnClickListener { viewModel.setGalleryFilter(GalleryFilter.DOCS) }
        binding.chipAudio.setOnClickListener { viewModel.setGalleryFilter(GalleryFilter.AUDIO) }
        binding.chipPending.setOnClickListener { viewModel.setGalleryFilter(GalleryFilter.PENDING) }
        binding.chipUploaded.setOnClickListener { viewModel.setGalleryFilter(GalleryFilter.UPLOADED) }
    }

    private fun setupUploadButton() {
        binding.btnUploadSelection.setOnClickListener {
            if (!viewModel.prefs.isConfigured()) {
                Toast.makeText(
                    requireContext(),
                    "Please configure Telegram Bot Token and Channel ID in Settings!",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            viewModel.startOrResumeUploads()
            Toast.makeText(requireContext(), "🚀 Started uploading unsent media!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.galleryItems.collect { items ->
                    adapter.submitList(items)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
