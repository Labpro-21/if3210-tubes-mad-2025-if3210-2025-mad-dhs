package com.tubes.purry.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.util.Log
import com.tubes.purry.data.model.Song
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.tubes.purry.R
import com.tubes.purry.R.raw.terlintas
import com.tubes.purry.data.model.LikedSong
import com.tubes.purry.data.model.ProfileData
import com.tubes.purry.data.model.ListeningSession

@Database(entities = [Song::class, LikedSong::class, ProfileData::class, ListeningSession::class], version = 7)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun LikedSongDao(): LikedSongDao
    abstract fun analyticsDao(): AnalyticsDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "purritify.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(SeedDatabaseCallback())
                    .build()
                    .also { INSTANCE = it }

                instance
            }
        }
    }

    private class SeedDatabaseCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            Log.d("AppDatabase", "onCreate triggered. Seeding data...")

            // Create triggers for likedSongs counter
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS increment_liked_songs
                AFTER INSERT ON liked_songs
                BEGIN
                  UPDATE user_profile
                  SET likedSongs = likedSongs + 1
                  WHERE id = NEW.userId;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS decrement_liked_songs
                AFTER DELETE ON liked_songs
                BEGIN
                  UPDATE user_profile
                  SET likedSongs = likedSongs - 1
                  WHERE id = OLD.userId;
                END;
            """.trimIndent())

            CoroutineScope(Dispatchers.IO).launch {
                INSTANCE?.songDao()?.insertAll(predefinedSongs())
                Log.d("AppDatabase", "Seeding done.")
            }
        }

        private fun predefinedSongs(): List<Song> = listOf(
            Song(
                id = "1",
                serverId = null,
                title = "Katakan Saja",
                artist = "Adikara",
                coverResId = R.drawable.katakan_saja,
                coverPath = null,
                filePath = null,
                resId = R.raw.katakan_saja,
                duration = 239000,
                isLiked = false,
                isLocal = true,
                lastPlayedAt = 0L,
                uploadedBy = -1
            ),
            Song(
                id = "2",
                serverId = null,
                title = "Primadona",
                artist = "Adikara",
                coverResId = R.drawable.primadona,
                coverPath = null,
                filePath = null,
                resId = R.raw.primadona,
                duration = 247000,
                isLiked = false,
                isLocal = true,
                lastPlayedAt = 0L,
                uploadedBy = -1
            ),
            Song(
                id = "3",
                serverId = null,
                title = "Terlintas",
                artist = "Bernadya",
                coverResId = R.drawable.terlintas,
                coverPath = null,
                filePath = null,
                resId = terlintas,
                duration = 235000,
                isLiked = false,
                isLocal = true,
                lastPlayedAt = 0L,
                uploadedBy = -1
            )
        )
    }
}
