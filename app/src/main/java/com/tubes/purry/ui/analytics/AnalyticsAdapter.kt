package com.tubes.purry.ui.analytics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tubes.purry.R
import com.tubes.purry.databinding.ItemSoundCapsuleMonthBinding
import com.tubes.purry.databinding.ItemTopSongBinding
import com.tubes.purry.databinding.ItemTopArtistBinding
import com.tubes.purry.data.model.TopSong
import com.tubes.purry.data.model.TopArtist

// Sound Capsule Adapter
class SoundCapsuleAdapter(
    private val onMonthClick: (String) -> Unit,
    private val onExportClick: (String) -> Unit
) : ListAdapter<MonthlyAnalyticsSummary, SoundCapsuleAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSoundCapsuleMonthBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSoundCapsuleMonthBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MonthlyAnalyticsSummary) {
            binding.apply {
                tvMonthTitle.text = item.displayName
                tvTotalMinutes.text = "${item.totalMinutes} minutes"
                tvTopArtist.text = item.topArtist ?: "No data"
                tvTopSong.text = item.topSong ?: "No data"
                tvStreaks.text = if (item.dayStreaks > 0) "${item.dayStreaks} streaks" else "No streaks"

                root.setOnClickListener { onMonthClick(item.month) }
                btnExport.setOnClickListener { onExportClick(item.month) }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MonthlyAnalyticsSummary>() {
        override fun areItemsTheSame(
            oldItem: MonthlyAnalyticsSummary,
            newItem: MonthlyAnalyticsSummary
        ): Boolean = oldItem.month == newItem.month

        override fun areContentsTheSame(
            oldItem: MonthlyAnalyticsSummary,
            newItem: MonthlyAnalyticsSummary
        ): Boolean = oldItem == newItem
    }
}

// Top Songs Adapter
class TopSongsAdapter : ListAdapter<TopSong, TopSongsAdapter.ViewHolder>(TopSongDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTopSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTopSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: TopSong) {
            binding.apply {
                tvRank.text = String.format("%02d", song.rank)
                tvSongTitle.text = song.title
                tvArtist.text = song.artist
                tvPlayCount.text = "${song.playCount} plays"

                if (song.cover != null) {
                    Glide.with(itemView.context)
                        .load(song.cover)
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_music_note)
                        .into(ivCover)
                } else {
                    ivCover.setImageResource(R.drawable.ic_music_note)
                }
            }
        }
    }

    companion object TopSongDiffCallback : DiffUtil.ItemCallback<TopSong>() {
        override fun areItemsTheSame(oldItem: TopSong, newItem: TopSong): Boolean =
            oldItem.rank == newItem.rank && oldItem.title == newItem.title

        override fun areContentsTheSame(oldItem: TopSong, newItem: TopSong): Boolean =
            oldItem == newItem
    }
}

// Top Artists Adapter
class TopArtistsAdapter : ListAdapter<TopArtist, TopArtistsAdapter.ViewHolder>(TopArtistDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTopArtistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTopArtistBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(artist: TopArtist) {
            binding.apply {
                tvRank.text = String.format("%02d", artist.rank)
                tvArtistName.text = artist.name
                tvPlayCount.text = "${artist.playCount} plays"

                if (artist.cover != null) {
                    Glide.with(itemView.context)
                        .load(artist.cover)
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_music_note)
                        .into(ivCover)
                } else {
                    ivCover.setImageResource(R.drawable.ic_music_note)
                }
            }
        }
    }

    companion object TopArtistDiffCallback : DiffUtil.ItemCallback<TopArtist>() {
        override fun areItemsTheSame(oldItem: TopArtist, newItem: TopArtist): Boolean =
            oldItem.rank == newItem.rank && oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: TopArtist, newItem: TopArtist): Boolean =
            oldItem == newItem
    }
}

// ViewModel Factory
class SoundCapsuleViewModelFactory(private val context: android.content.Context) :
    androidx.lifecycle.ViewModelProvider.Factory {

    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SoundCapsuleViewModel::class.java)) {
            val database = com.tubes.purry.data.local.AppDatabase.getDatabase(context)
            val analyticsRepository = com.tubes.purry.data.repository.AnalyticsRepository(
                database.analyticsDao(),
                database.songDao()
            )

            // Create ProfileRepository with proper ApiService
            val apiService = com.tubes.purry.data.remote.ApiClient.apiService
            val sessionManager = com.tubes.purry.utils.SessionManager(context)
            val authRepository = com.tubes.purry.data.repository.AuthRepository(apiService, sessionManager)
            val profileRepository = com.tubes.purry.data.repository.ProfileRepository(apiService, authRepository)

            val exportService = AnalyticsExportService(context)

            @Suppress("UNCHECKED_CAST")
            return SoundCapsuleViewModel(
                context.applicationContext as android.app.Application,
                analyticsRepository,
                profileRepository,
                exportService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}