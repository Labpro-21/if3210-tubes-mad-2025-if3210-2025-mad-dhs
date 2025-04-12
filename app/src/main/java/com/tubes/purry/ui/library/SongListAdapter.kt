package com.tubes.purry.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.tubes.purry.R
import com.tubes.purry.databinding.ItemSongListBinding
import com.tubes.purry.data.model.Song
import com.tubes.purry.utils.SessionManager

class SongListAdapter(
    private val onClick: (Song) -> Unit,
    private val onEdit: (Song) -> Unit,
    private val onDelete: (Song) -> Unit
) : RecyclerView.Adapter<SongListAdapter.SongViewHolder>() {

    private var songs = listOf<Song>()

    fun submitList(list: List<Song>) {
        songs = list
        notifyDataSetChanged()
    }

    fun getSongAt(position: Int): Song {
        return songs[position]
    }


    inner class SongViewHolder(private val binding: ItemSongListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            val context = binding.root.context
            val sessionManager = SessionManager(context)
            val currentUserId = sessionManager.getUserId()
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

            if (song.uploadedBy == currentUserId) {
                binding.btnEdit.visibility = View.VISIBLE
                binding.btnDelete.visibility = View.VISIBLE
            } else {
                binding.btnEdit.visibility = View.GONE
                binding.btnDelete.visibility = View.GONE
            }

            binding.root.setOnClickListener { onClick(song) }
            binding.btnEdit.setOnClickListener { onEdit(song) }
            binding.btnDelete.setOnClickListener { onDelete(song) }
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
