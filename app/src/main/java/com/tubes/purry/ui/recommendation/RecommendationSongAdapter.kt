package com.tubes.purry.ui.recommendation

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tubes.purry.R
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.data.model.OnlineSong
import com.tubes.purry.data.model.Song
import com.tubes.purry.utils.DownloadUtils
import com.tubes.purry.utils.parseDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class RecommendationSongAdapter(
    private var songs: List<Song>,
    private val context: Context,
    private val onClick: (Song) -> Unit,
    private val country: String
) : RecyclerView.Adapter<RecommendationSongAdapter.ViewHolder>() {

    private val downloadingSongs = mutableSetOf<String>()
    private val downloadedSongs = mutableSetOf<String>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cover: ImageView = view.findViewById(R.id.imgCover)
        private val title: TextView = view.findViewById(R.id.tvTitle)
        private val artist: TextView = view.findViewById(R.id.tvArtist)
        private val btnDownload: ImageButton = view.findViewById(R.id.btnEdit)
        private val progressBar: ProgressBar = view.findViewById(R.id.downloadProgress)
        private val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)

        fun bind(song: Song) {
            title.text = song.title
            artist.text = song.artist

            Glide.with(itemView.context)
                .load(song.coverPath ?: R.drawable.album_default)
                .placeholder(R.drawable.album_default)
                .into(cover)

            itemView.setOnClickListener {
                onClick(song)
            }

            btnDelete.visibility = View.GONE

            when {
                downloadingSongs.contains(song.id) -> {
                    btnDownload.visibility = View.INVISIBLE
                    progressBar.visibility = View.VISIBLE
                }
                downloadedSongs.contains(song.id) -> {
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
                        downloadingSongs.add(song.id)
                        notifyItemChanged(adapterPosition)

                        val online = OnlineSong(
                            id = song.serverId ?: 0,
                            title = song.title,
                            artist = song.artist,
                            url = song.filePath ?: "",
                            artwork = song.coverPath ?: "",
                            duration = song.duration.toString(),
                            rank = 0,
                            country = country
                        )

                        DownloadUtils.downloadSong(context, online) { file ->
                            if (file != null) {
                                Toast.makeText(context, "Unduh selesai: ${song.title}", Toast.LENGTH_SHORT).show()
                                val localSong = song.copy(
                                    id = UUID.randomUUID().toString(),
                                    filePath = file.absolutePath,
                                    isLocal = true
                                )
                                CoroutineScope(Dispatchers.IO).launch {
                                    AppDatabase.getDatabase(context).songDao().insert(localSong)
                                }
                                downloadedSongs.add(song.id)
                            } else {
                                Toast.makeText(context, "Gagal download lagu", Toast.LENGTH_SHORT).show()
                            }
                            downloadingSongs.remove(song.id)
                            notifyItemChanged(adapterPosition)
                        }
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

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    fun checkDownloadedStatus() {
        downloadedSongs.clear()
        songs.forEach {
            val fileName = "${it.title}_${it.artist}.mp3"
                .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val file = File(context.getExternalFilesDir(null), "PurryMusic/$fileName")
            if (file.exists()) {
                downloadedSongs.add(it.id)
            }
        }
        notifyDataSetChanged()
    }
}