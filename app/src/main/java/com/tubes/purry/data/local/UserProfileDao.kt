package com.tubes.purry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tubes.purry.data.model.ProfileData

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: ProfileData)

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getProfile(): ProfileData?

    @Query("DELETE FROM user_profile")
    suspend fun clearProfile()

    @Query("SELECT * FROM user_profile WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Int): ProfileData?
}
