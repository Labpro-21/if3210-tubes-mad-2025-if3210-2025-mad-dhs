package com.tubes.purry.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tubes.purry.R
import com.tubes.purry.data.model.Song
import com.tubes.purry.databinding.ItemSongListBinding

class RecommendationAdapter(
    private val onClick: (Song) -> Unit
) : ListAdapter<Song, RecommendationAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSongListBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSongListBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.apply {
                // Use the correct view IDs from your layout
                tvTitle.text = song.title
                tvArtist.text = song.artist

                when {
                    song.coverResId != null -> {
                        Glide.with(binding.root).load(song.coverResId).into(binding.imgCover)
                    }
                    !song.coverPath.isNullOrBlank() -> {
                        Glide.with(binding.root).load(song.coverPath.toUri()).into(binding.imgCover)
                    }
                    else -> {
                        binding.imgCover.setImageResource(R.drawable.album_default)
                    }
                }

                // Hide edit and delete buttons for recommendations
                btnEdit.visibility = android.view.View.GONE
                btnDelete.visibility = android.view.View.GONE

                root.setOnClickListener { onClick(song) }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem == newItem
        }
    }

    fun getSongAt(position: Int): Song = getItem(position)
}