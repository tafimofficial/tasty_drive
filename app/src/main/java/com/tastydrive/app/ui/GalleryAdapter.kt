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

    inner class GalleryViewHolder(private val binding: ItemGalleryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaItem) {
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

            when (item.status) {
                UploadStatus.COMPLETED -> {
                    binding.ivGalleryStatus.visibility = View.VISIBLE
                    binding.ivGalleryStatus.setImageResource(android.R.drawable.checkbox_on_background)
                    binding.ivGalleryStatus.setColorFilter(
                        ContextCompat.getColor(binding.root.context, R.color.status_green)
                    )
                }
                UploadStatus.OVERSIZED -> {
                    binding.ivGalleryStatus.visibility = View.VISIBLE
                    binding.ivGalleryStatus.setImageResource(R.drawable.ic_nav_large)
                    binding.ivGalleryStatus.setColorFilter(
                        ContextCompat.getColor(binding.root.context, R.color.status_blue)
                    )
                }
                UploadStatus.UPLOADING -> {
                    binding.ivGalleryStatus.visibility = View.VISIBLE
                    binding.ivGalleryStatus.setImageResource(android.R.drawable.ic_popup_sync)
                    binding.ivGalleryStatus.setColorFilter(
                        ContextCompat.getColor(binding.root.context, R.color.primary)
                    )
                }
                else -> {
                    binding.ivGalleryStatus.visibility = View.GONE
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
            return oldItem.status == newItem.status && oldItem.size == newItem.size
        }
    }
}
