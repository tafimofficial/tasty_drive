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
import com.tastydrive.app.databinding.ItemLargeFileBinding
import com.tastydrive.app.util.NativeEngine

class LargeFilesAdapter(
    private val onSelectionChanged: (selectedCount: Int) -> Unit,
    private val onItemClick: (MediaItem) -> Unit
) : ListAdapter<MediaItem, LargeFilesAdapter.LargeViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LargeViewHolder {
        val binding = ItemLargeFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LargeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LargeViewHolder, position: Int) {
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

    inner class LargeViewHolder(private val binding: ItemLargeFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaItem) {
            val ctx = binding.root.context
            binding.tvLargeName.text = item.displayName

            // Category styling
            when (item.category) {
                FileCategory.APKS -> {
                    binding.tvLargeType.text = "APK File (>50MB) 🤖"
                    binding.ivLargeThumb.visibility = View.GONE
                    binding.ivLargeVideoBadge.visibility = View.GONE
                    binding.ivLargeCategoryIcon.visibility = View.VISIBLE
                    binding.ivLargeCategoryIcon.setImageResource(R.drawable.ic_file_apk)
                }
                FileCategory.PDFS -> {
                    binding.tvLargeType.text = "PDF Document (>50MB) 📄"
                    binding.ivLargeThumb.visibility = View.GONE
                    binding.ivLargeVideoBadge.visibility = View.GONE
                    binding.ivLargeCategoryIcon.visibility = View.VISIBLE
                    binding.ivLargeCategoryIcon.setImageResource(R.drawable.ic_file_pdf)
                }
                FileCategory.DOCUMENTS -> {
                    binding.tvLargeType.text = "Large Document / Archive 📑"
                    binding.ivLargeThumb.visibility = View.GONE
                    binding.ivLargeVideoBadge.visibility = View.GONE
                    binding.ivLargeCategoryIcon.visibility = View.VISIBLE
                    binding.ivLargeCategoryIcon.setImageResource(R.drawable.ic_file_doc)
                }
                FileCategory.AUDIO -> {
                    binding.tvLargeType.text = "Audio / Music (>50MB) 🎵"
                    binding.ivLargeThumb.visibility = View.GONE
                    binding.ivLargeVideoBadge.visibility = View.GONE
                    binding.ivLargeCategoryIcon.visibility = View.VISIBLE
                    binding.ivLargeCategoryIcon.setImageResource(R.drawable.ic_file_audio)
                }
                FileCategory.VIDEOS -> {
                    binding.tvLargeType.text = "Video File (>50MB) 🎥"
                    binding.ivLargeThumb.visibility = View.VISIBLE
                    binding.ivLargeCategoryIcon.visibility = View.GONE
                    binding.ivLargeVideoBadge.visibility = View.VISIBLE

                    Glide.with(binding.ivLargeThumb.context)
                        .load(item.uri)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .placeholder(R.drawable.bg_card_rounded)
                        .into(binding.ivLargeThumb)
                }
                FileCategory.PHOTOS -> {
                    binding.tvLargeType.text = "High-Res Photo (>50MB) 📸"
                    binding.ivLargeThumb.visibility = View.VISIBLE
                    binding.ivLargeCategoryIcon.visibility = View.GONE
                    binding.ivLargeVideoBadge.visibility = View.GONE

                    Glide.with(binding.ivLargeThumb.context)
                        .load(item.uri)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .placeholder(R.drawable.bg_card_rounded)
                        .into(binding.ivLargeThumb)
                }
                else -> {
                    binding.tvLargeType.text = "Large File (>50MB) 📁"
                    binding.ivLargeThumb.visibility = View.GONE
                    binding.ivLargeVideoBadge.visibility = View.GONE
                    binding.ivLargeCategoryIcon.visibility = View.VISIBLE
                    binding.ivLargeCategoryIcon.setImageResource(R.drawable.ic_file_doc)
                }
            }

            // Upload Status, Checkbox & Badge Handling
            when (item.status) {
                UploadStatus.COMPLETED -> {
                    // Item is ALREADY UPLOADED: Show green checkmark, hide checkbox, disable selection
                    binding.cbSelectLarge.visibility = View.GONE
                    binding.ivLargeCompleted.visibility = View.VISIBLE
                    binding.tvLargeSizeBadge.text = "Uploaded ⚡"
                    binding.tvLargeSizeBadge.setTextColor(ContextCompat.getColor(ctx, R.color.status_green))
                    binding.root.alpha = 0.85f

                    binding.cbSelectLarge.setOnCheckedChangeListener(null)
                    binding.root.setOnClickListener {
                        onItemClick(item)
                    }
                }
                UploadStatus.UPLOADING -> {
                    // Item is currently uploading via Local Docker
                    binding.cbSelectLarge.visibility = View.GONE
                    binding.ivLargeCompleted.visibility = View.GONE
                    val pct = item.progress
                    binding.tvLargeSizeBadge.text = "$pct% 🚀"
                    binding.tvLargeSizeBadge.setTextColor(ContextCompat.getColor(ctx, R.color.status_blue))
                    binding.root.alpha = 1.0f

                    binding.cbSelectLarge.setOnCheckedChangeListener(null)
                    binding.root.setOnClickListener {
                        onItemClick(item)
                    }
                }
                else -> {
                    // Item is PENDING (Queued / Oversized / Failed / Paused): Selectable with checkbox
                    binding.cbSelectLarge.visibility = View.VISIBLE
                    binding.ivLargeCompleted.visibility = View.GONE
                    binding.tvLargeSizeBadge.text = NativeEngine.safeFormatFileSize(item.size)
                    binding.tvLargeSizeBadge.setTextColor(ContextCompat.getColor(ctx, R.color.status_red))
                    binding.root.alpha = 1.0f

                    binding.cbSelectLarge.setOnCheckedChangeListener(null)
                    binding.cbSelectLarge.isChecked = item.isSelected

                    binding.cbSelectLarge.setOnCheckedChangeListener { _, isChecked ->
                        item.isSelected = isChecked
                        onSelectionChanged(getSelectedItems().size)
                    }

                    binding.root.setOnClickListener {
                        item.isSelected = !item.isSelected
                        binding.cbSelectLarge.isChecked = item.isSelected
                        onSelectionChanged(getSelectedItems().size)
                        onItemClick(item)
                    }
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
                    oldItem.progress == newItem.progress &&
                    oldItem.size == newItem.size
        }
    }
}
