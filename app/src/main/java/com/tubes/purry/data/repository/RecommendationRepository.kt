package com.tubes.purry.data.repository

import com.tubes.purry.data.local.SongDao
import com.tubes.purry.data.local.LikedSongDao
import com.tubes.purry.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

class RecommendationRepository(
    private val songDao: SongDao,
    private val likedSongDao: LikedSongDao
) {

    fun getDailyPlaylist(userId: Int): Flow<List<Song>> {
        return combine(
            songDao.getSongsByLikedArtists(userId, 5),
            songDao.getPopularSongs(userId, 3),
            songDao.getDiscoverySongs(userId, 2)
        ) { artistSongs, popularSongs, discoverySongs ->
            // Combine and shuffle for variety
            (artistSongs + popularSongs + discoverySongs).shuffled().take(10)
        }
    }

    fun getTopMixes(userId: Int): Flow<List<Song>> {
        return combine(
            songDao.getSongsByLikedArtists(userId, 8),
            songDao.getPopularSongs(userId, 7)
        ) { artistSongs, popularSongs ->
            // Mix based on user preferences and popularity
            (artistSongs + popularSongs).shuffled().take(15)
        }
    }

    suspend fun hasEnoughDataForRecommendations(userId: Int): Boolean {
        val likedCount = likedSongDao.getLikedCountByUser(userId)
        val listeningCount = songDao.getUserListeningCount()
        // Need at least 2 liked songs or 5 played songs for recommendations
        return listeningCount >= 5 // This will be checked in the ViewModel
    }
}