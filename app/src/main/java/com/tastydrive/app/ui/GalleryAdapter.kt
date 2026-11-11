package com.tastydrive.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.tastydrive.app.R
import com.tastydrive.app.data.FileCategory
import com.tastydrive.app.data.MediaItem
import com.tastydrive.app.data.UploadStatus
import com.tastydrive.app.databinding.ItemGalleryBinding
import com.tastydrive.app.util.NativeEngine

class GalleryAdapter(
    private val onSelectionChanged: (selectedCount: Int) -> Unit,
    private val onItemClick: (MediaItem) -> Unit
) : ListAdapter<MediaItem, GalleryAdapter.GalleryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val binding = ItemGalleryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GalleryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun selectAll(select: Boolean) {
        currentList.forEach { item ->
            if (item.status != UploadStatus.COMPLETED) {
                item.isSelected = select
            } else {
                item.isSelected = false
            }
        }
        notifyDataSetChanged()
        onSelectionChanged(getSelectedItems().size)
    }

    fun getSelectedItems(): List<MediaItem> {
        return currentList.filter { it.isSelected && it.status != UploadStatus.COMPLETED }
    }

    fun clearSelection() {
        selectAll(false)
    }

    inner class GalleryViewHolder(private val binding: ItemGalleryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaItem) {
            val ctx = binding.root.context
            binding.tvGalleryFileSize.text = NativeEngine.safeFormatFileSize(item.size)

            when (item.category) {
                FileCategory.PHOTOS, FileCategory.VIDEOS -> {
                    binding.layoutIconCenter.visibility = View.GONE
                    binding.ivGalleryThumb.visibility = View.VISIBLE
                    binding.ivGalleryVideoBadge.visibility = if (item.isVideo) View.VISIBLE else View.GONE

                    Glide.with(binding.ivGalleryThumb.context)
                        .load(item.uri)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .placeholder(R.drawable.bg_card_rounded)
                        .into(binding.ivGalleryThumb)
                }
                FileCategory.APKS -> {
                    binding.ivGalleryThumb.visibility = View.GONE
                    binding.ivGalleryVideoBadge.visibility = View.GONE
                    binding.layoutIconCenter.visibility = View.VISIBLE
                    binding.ivGalleryCategoryIcon.setImageResource(R.drawable.ic_file_apk)
                    binding.tvGalleryFileName.text = item.displayName
                }
                FileCategory.PDFS -> {
                    binding.ivGalleryThumb.visibility = View.GONE
                    binding.ivGalleryVideoBadge.visibility = View.GONE
                    binding.layoutIconCenter.visibility = View.VISIBLE
                    binding.ivGalleryCategoryIcon.setImageResource(R.drawable.ic_file_pdf)
                    binding.tvGalleryFileName.text = item.displayName
                }
                FileCategory.DOCUMENTS -> {
                    binding.ivGalleryThumb.visibility = View.GONE
                    binding.ivGalleryVideoBadge.visibility = View.GONE
                    binding.layoutIconCenter.visibility = View.VISIBLE
                    binding.ivGalleryCategoryIcon.setImageResource(R.drawable.ic_file_doc)
                    binding.tvGalleryFileName.text = item.displayName
                }
                FileCategory.AUDIO -> {
                    binding.ivGalleryThumb.visibility = View.GONE
                    binding.ivGalleryVideoBadge.visibility = View.GONE
                    binding.layoutIconCenter.visibility = View.VISIBLE
                    binding.ivGalleryCategoryIcon.setImageResource(R.drawable.ic_file_audio)
                    binding.tvGalleryFileName.text = item.displayName
                }
                else -> {
                    binding.ivGalleryThumb.visibility = View.GONE
                    binding.ivGalleryVideoBadge.visibility = View.GONE
                    binding.layoutIconCenter.visibility = View.VISIBLE
                    binding.ivGalleryCategoryIcon.setImageResource(R.drawable.ic_file_doc)
                    binding.tvGalleryFileName.text = item.displayName
                }
            }

            // Status and Selection badge handling
            when (item.status) {
                UploadStatus.COMPLETED -> {
                    binding.ivGalleryStatus.visibility = View.VISIBLE
                    binding.ivGalleryStatus.setImageResource(R.drawable.ic_check_circle)
                    binding.ivGalleryStatus.setColorFilter(
                        ContextCompat.getColor(ctx, R.color.status_green)
                    )
                    binding.ivGallerySelect.visibility = View.GONE
                    binding.viewSelectionOverlay.visibility = View.GONE
                }
                UploadStatus.UPLOADING -> {
                    binding.ivGalleryStatus.visibility = View.VISIBLE
                    binding.ivGalleryStatus.setImageResource(android.R.drawable.ic_popup_sync)
                    binding.ivGalleryStatus.setColorFilter(
                        ContextCompat.getColor(ctx, R.color.primary)
                    )
                    binding.ivGallerySelect.visibility = View.GONE
                    binding.viewSelectionOverlay.visibility = View.GONE
                }
                else -> {
                    binding.ivGalleryStatus.visibility = View.GONE
                    binding.ivGallerySelect.visibility = View.VISIBLE

                    if (item.isSelected) {
                        binding.ivGallerySelect.setImageResource(R.drawable.ic_gallery_select_check)
                        binding.viewSelectionOverlay.visibility = View.VISIBLE
                    } else {
                        binding.ivGallerySelect.setImageResource(R.drawable.ic_gallery_select_uncheck)
                        binding.viewSelectionOverlay.visibility = View.GONE
                    }
                }
            }

            // Clicking toggles selection on pending items, or alerts on already completed items
            binding.root.setOnClickListener {
                if (item.status == UploadStatus.COMPLETED) {
                    onItemClick(item)
                } else {
                    item.isSelected = !item.isSelected
                    if (item.isSelected) {
                        binding.ivGallerySelect.setImageResource(R.drawable.ic_gallery_select_check)
                        binding.viewSelectionOverlay.visibility = View.VISIBLE
                    } else {
                        binding.ivGallerySelect.setImageResource(R.drawable.ic_gallery_select_uncheck)
                        binding.viewSelectionOverlay.visibility = View.GONE
                    }
                    onSelectionChanged(getSelectedItems().size)
                    onItemClick(item)
                }
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.isSelected == newItem.isSelected &&
                    oldItem.status == newItem.status &&
                    oldItem.size == newItem.size
        }
    }
}
