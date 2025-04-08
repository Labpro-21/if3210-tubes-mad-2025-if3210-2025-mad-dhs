package com.tubes.purry.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.tubes.purry.R
import com.tubes.purry.databinding.ItemSongListBinding
import com.tubes.purry.data.model.Song

class SongListAdapter(
    private val onClick: (Song) -> Unit
) : RecyclerView.Adapter<SongListAdapter.SongViewHolder>() {

    private var songs = listOf<Song>()

    fun submitList(list: List<Song>) {
        songs = list
        notifyDataSetChanged()
    }

    inner class SongViewHolder(private val binding: ItemSongListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist

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

            binding.root.setOnClickListener { onClick(song) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongListBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount(): Int = songs.size
}
