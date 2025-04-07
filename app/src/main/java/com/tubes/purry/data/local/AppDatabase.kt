package com.tubes.purry.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tubes.purry.data.model.Song
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Database(entities = [Song::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "purritify.db"
                )
                    .addCallback(DatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val context: Context
    ) : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                getDatabase(context).songDao().insertAll(predefinedSongs())
            }
        }

        private fun predefinedSongs(): List<Song> = listOf(
            Song(
                id = "1",
                title = "Katakan Saja",
                artist = "Adikara",
                coveredUrl = "https://i.ibb.co/rKzzzDv9/katakan-saja.jpg",
                filePath = "katakan_saja",
                duration = 239,
                isLiked = false,
                isLocal = true,
                lastPlayedAt = 0L
            ),
            Song(
                id = "2",
                title = "Primadona",
                artist = "Adikara",
                coveredUrl = "https://i.ibb.co/prZxvjdP/primadona.jpg",
                filePath = "primadona",
                duration = 247,
                isLiked = false,
                isLocal = true,
                lastPlayedAt = 0L
            ),
            Song(
                id = "3",
                title = "Terlintas",
                artist = "Bernadya",
                coveredUrl = "https://i.ibb.co/whbHgzBc/terlintas.png",
                filePath = "terlintas",
                duration = 235,
                isLiked = false,
                isLocal = true,
                lastPlayedAt = 0L
            )
        )
    }
}
