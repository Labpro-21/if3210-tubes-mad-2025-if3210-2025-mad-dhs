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

@Database(entities = [Song::class], version = 3)
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

            CoroutineScope(Dispatchers.IO).launch {
                INSTANCE?.songDao()?.insertAll(predefinedSongs())
                Log.d("AppDatabase", "Seeding done.")
            }
        }

        private fun predefinedSongs(): List<Song> = listOf(
            Song(
                id = "1",
                title = "Katakan Saja",
                artist = "Adikara",
                coverResId = R.drawable.katakan_saja,
                coverPath = null,
                filePath = null,
                resId = R.raw.katakan_saja,
                duration = 239,
                isLiked = false,
                isLocal = true,
                lastPlayedAt = 0L,
                uploadedBy = -1
            ),
            Song(
                id = "2",
                title = "Primadona",
                artist = "Adikara",
                coverResId = R.drawable.primadona,
                coverPath = null,
                filePath = null,
                resId = R.raw.primadona,
                duration = 247,
                isLiked = false,
                isLocal = true,
                lastPlayedAt = 0L,
                uploadedBy = -1
            ),
            Song(
                id = "3",
                title = "Terlintas",
                artist = "Bernadya",
                coverResId = R.drawable.terlintas,
                coverPath = null,
                filePath = null,
                resId = R.raw.terlintas,
                duration = 235,
                isLiked = false,
                isLocal = true,
                lastPlayedAt = 0L,
                uploadedBy = -1
            )
        )
    }
}
