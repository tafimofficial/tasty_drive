package com.tastydrive.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.tastydrive.app.R
import com.tastydrive.app.data.FileCategory
import com.tastydrive.app.data.MediaItem
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
        currentList.forEach { it.isSelected = select }
        notifyDataSetChanged()
        onSelectionChanged(getSelectedItems().size)
    }

    fun getSelectedItems(): List<MediaItem> {
        return currentList.filter { it.isSelected }
    }

    inner class LargeViewHolder(private val binding: ItemLargeFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaItem) {
            binding.tvLargeName.text = item.displayName
            binding.tvLargeSizeBadge.text = NativeEngine.safeFormatFileSize(item.size)

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

    object DiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.isSelected == newItem.isSelected && oldItem.size == newItem.size
        }
    }
}
