package com.tubes.purry.ui.chart

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tubes.purry.R
import com.tubes.purry.data.model.OnlineSong
import java.io.File

class OnlineSongListAdapter(
    private var songs: List<OnlineSong>,
    private val onClick: (OnlineSong) -> Unit,
    private val onDownloadClick: (OnlineSong) -> Unit
) : RecyclerView.Adapter<OnlineSongListAdapter.ViewHolder>() {

    private val downloadingSongs = mutableSetOf<String>()
    private val downloadedSongs = mutableSetOf<String>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cover: ImageView = view.findViewById(R.id.imgCover)
        private val title: TextView = view.findViewById(R.id.tvTitle)
        private val artist: TextView = view.findViewById(R.id.tvArtist)
        private val btnDownload: ImageButton = view.findViewById(R.id.btnEdit)
        private val progressBar: ProgressBar = view.findViewById(R.id.downloadProgress)
        private val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)

        fun bind(song: OnlineSong) {
            title.text = "${song.rank}. ${song.title}"
            artist.text = song.artist

            Glide.with(itemView.context)
                .load(song.artwork)
                .placeholder(R.drawable.album_default)
                .into(cover)

            itemView.setOnClickListener {
                onClick(song)
            }

            btnDelete.visibility = View.GONE

            when {
                downloadingSongs.contains(song.url) -> {
                    btnDownload.visibility = View.INVISIBLE
                    progressBar.visibility = View.VISIBLE
                }
                downloadedSongs.contains(song.url) -> {
                    progressBar.visibility = View.GONE
                    btnDownload.visibility = View.VISIBLE
                    btnDownload.setImageResource(R.drawable.ic_downloaded)
                    btnDownload.setColorFilter(itemView.context.getColor(android.R.color.white))
                    btnDownload.setOnClickListener(null)
                }
                else -> {
                    progressBar.visibility = View.GONE
                    btnDownload.visibility = View.VISIBLE
                    btnDownload.setImageResource(R.drawable.ic_download)
                    btnDownload.setColorFilter(itemView.context.getColor(android.R.color.white))
                    btnDownload.setOnClickListener {
                        downloadingSongs.add(song.url)
                        notifyItemChanged(adapterPosition)
                        onDownloadClick(song)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount(): Int = songs.size

    fun updateSongs(newSongs: List<OnlineSong>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    fun markAsDownloading(songUrl: String) {
        downloadingSongs.add(songUrl)
        notifyItemChanged(findIndex(songUrl))
    }

    fun markAsDownloaded(songUrl: String) {
        downloadingSongs.remove(songUrl)
        downloadedSongs.add(songUrl)
        notifyItemChanged(findIndex(songUrl))
    }

    private fun findIndex(songUrl: String): Int {
        return songs.indexOfFirst { it.url == songUrl }
    }

    fun checkDownloadedStatus(context: Context) {
        downloadedSongs.clear()
        songs.forEach {
            val fileName = "${it.title}_${it.artist}.mp3"
                .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val file = File(context.getExternalFilesDir(null), "PurryMusic/$fileName")
            if (file.exists()) {
                downloadedSongs.add(it.url)
            }
        }
        notifyDataSetChanged()
    }
}
