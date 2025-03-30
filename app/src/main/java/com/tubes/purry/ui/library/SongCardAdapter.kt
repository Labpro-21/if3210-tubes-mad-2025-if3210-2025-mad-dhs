package com.tubes.purry.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tubes.purry.databinding.ItemSongCardBinding
import com.tubes.purry.data.model.Song

class SongCardAdapter(
    private val onClick: (Song) -> Unit
) : RecyclerView.Adapter<SongCardAdapter.SongViewHolder>() {

    private var songs = listOf<Song>()

    fun submitList(list: List<Song>) {
        songs = list
        notifyDataSetChanged()
    }

    inner class SongViewHolder(private val binding: ItemSongCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist

            Glide.with(binding.root)
                .load(song.coveredUrl)
                .into(binding.imgCover)

            binding.root.setOnClickListener { onClick(song) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount(): Int = songs.size
}
