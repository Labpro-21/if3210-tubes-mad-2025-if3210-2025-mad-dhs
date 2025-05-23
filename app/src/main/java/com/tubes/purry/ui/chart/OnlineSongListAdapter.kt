package com.tubes.purry.ui.chart

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tubes.purry.R
import com.tubes.purry.data.model.OnlineSong
import android.util.Log

class OnlineSongListAdapter(
    private var songs: List<OnlineSong>,
    private val onClick: (OnlineSong) -> Unit,
    private val onDownloadClick: (OnlineSong) -> Unit
) : RecyclerView.Adapter<OnlineSongListAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cover: ImageView = view.findViewById(R.id.imgCover)
        private val title: TextView = view.findViewById(R.id.tvTitle)
        private val artist: TextView = view.findViewById(R.id.tvArtist)

        // Pakai btnEdit sebagai tombol download
        private val btnDownload: ImageButton = view.findViewById(R.id.btnEdit)

        // Sembunyikan tombol delete
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
                Log.d("TopChartDetail", "Clicked song: ${song.title}, url=${song.url}")

            }

            // Konfigurasi tombol download
            btnDownload.visibility = View.VISIBLE
            btnDownload.setImageResource(R.drawable.ic_download)
            btnDownload.contentDescription = "Download ${song.title}"
            btnDownload.setOnClickListener {
                onDownloadClick(song)
            }

            btnDelete.visibility = View.GONE
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
}
