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
import com.tastydrive.app.databinding.ItemQueueBinding
import com.tastydrive.app.util.NativeEngine

class QueueAdapter(
    private val onItemClick: (MediaItem) -> Unit
) : ListAdapter<MediaItem, QueueAdapter.QueueViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = ItemQueueBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QueueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class QueueViewHolder(private val binding: ItemQueueBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaItem) {
            binding.tvItemName.text = item.displayName
            binding.tvItemSize.text = NativeEngine.safeFormatFileSize(item.size)

            when (item.category) {
                FileCategory.APKS -> {
                    binding.tvItemType.text = "APK 🤖"
                    binding.ivItemThumb.visibility = View.GONE
                    binding.ivVideoBadge.visibility = View.GONE
                    binding.ivCategoryIcon.visibility = View.VISIBLE
                    binding.ivCategoryIcon.setImageResource(R.drawable.ic_file_apk)
                }
                FileCategory.PDFS -> {
                    binding.tvItemType.text = "PDF 📄"
                    binding.ivItemThumb.visibility = View.GONE
                    binding.ivVideoBadge.visibility = View.GONE
                    binding.ivCategoryIcon.visibility = View.VISIBLE
                    binding.ivCategoryIcon.setImageResource(R.drawable.ic_file_pdf)
                }
                FileCategory.DOCUMENTS -> {
                    binding.tvItemType.text = "Doc 📑"
                    binding.ivItemThumb.visibility = View.GONE
                    binding.ivVideoBadge.visibility = View.GONE
                    binding.ivCategoryIcon.visibility = View.VISIBLE
                    binding.ivCategoryIcon.setImageResource(R.drawable.ic_file_doc)
                }
                FileCategory.AUDIO -> {
                    binding.tvItemType.text = "Audio 🎵"
                    binding.ivItemThumb.visibility = View.GONE
                    binding.ivVideoBadge.visibility = View.GONE
                    binding.ivCategoryIcon.visibility = View.VISIBLE
                    binding.ivCategoryIcon.setImageResource(R.drawable.ic_file_audio)
                }
                FileCategory.VIDEOS -> {
                    binding.tvItemType.text = "Video 🎥"
                    binding.ivItemThumb.visibility = View.VISIBLE
                    binding.ivCategoryIcon.visibility = View.GONE
                    binding.ivVideoBadge.visibility = View.VISIBLE

                    Glide.with(binding.ivItemThumb.context)
                        .load(item.uri)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .placeholder(R.drawable.bg_card_rounded)
                        .into(binding.ivItemThumb)
                }
                FileCategory.PHOTOS -> {
                    binding.tvItemType.text = "Photo 📸"
                    binding.ivItemThumb.visibility = View.VISIBLE
                    binding.ivCategoryIcon.visibility = View.GONE
                    binding.ivVideoBadge.visibility = View.GONE

                    Glide.with(binding.ivItemThumb.context)
                        .load(item.uri)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .placeholder(R.drawable.bg_card_rounded)
                        .into(binding.ivItemThumb)
                }
                else -> {
                    binding.tvItemType.text = "File 📁"
                    binding.ivItemThumb.visibility = View.GONE
                    binding.ivVideoBadge.visibility = View.GONE
                    binding.ivCategoryIcon.visibility = View.VISIBLE
                    binding.ivCategoryIcon.setImageResource(R.drawable.ic_file_doc)
                }
            }

            // Status Badge and Progress
            when (item.status) {
                UploadStatus.QUEUED -> {
                    if (item.isOver50MB) {
                        binding.tvStatusBadge.text = "Needs Local API 💻"
                        binding.tvStatusBadge.setTextColor(ContextCompat.getColor(binding.root.context, R.color.status_blue))
                    } else {
                        binding.tvStatusBadge.text = "Queued ⏳"
                        binding.tvStatusBadge.setTextColor(ContextCompat.getColor(binding.root.context, R.color.status_orange))
                    }
                    binding.pbItemProgress.visibility = View.GONE
                    binding.tvItemError.visibility = View.GONE
                }
                UploadStatus.UPLOADING -> {
                    val targetStr = if (!item.uploadTarget.isNullOrEmpty()) " [${item.uploadTarget}]" else ""
                    binding.tvStatusBadge.text = "${item.progress}%$targetStr 🚀"
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(binding.root.context, R.color.status_green))
                    binding.pbItemProgress.visibility = View.VISIBLE
                    binding.pbItemProgress.progress = item.progress
                    binding.tvItemError.visibility = View.GONE
                }
                UploadStatus.COMPLETED -> {
                    binding.tvStatusBadge.text = "Uploaded ⚡"
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(binding.root.context, R.color.status_green))
                    binding.pbItemProgress.visibility = View.GONE
                    binding.tvItemError.visibility = View.GONE
                }
                UploadStatus.PAUSED -> {
                    binding.tvStatusBadge.text = "Paused ⏸"
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(binding.root.context, R.color.status_orange))
                    binding.pbItemProgress.visibility = View.GONE
                    binding.tvItemError.visibility = View.GONE
                }
                UploadStatus.FAILED -> {
                    binding.tvStatusBadge.text = "Failed ❌"
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(binding.root.context, R.color.status_red))
                    binding.pbItemProgress.visibility = View.GONE
                    if (!item.errorMessage.isNullOrEmpty()) {
                        binding.tvItemError.visibility = View.VISIBLE
                        binding.tvItemError.text = item.errorMessage
                    } else {
                        binding.tvItemError.visibility = View.GONE
                    }
                }
                UploadStatus.OVERSIZED -> {
                    binding.tvStatusBadge.text = ">50MB 📁"
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(binding.root.context, R.color.status_blue))
                    binding.pbItemProgress.visibility = View.GONE
                    binding.tvItemError.visibility = View.GONE
                }
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.status == newItem.status &&
                    oldItem.progress == newItem.progress &&
                    oldItem.errorMessage == newItem.errorMessage
        }
    }
}
