package com.tubes.purry.data.model

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey

//@Parcelize
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val filePath: String? = null,
    val resId: Int? = null,
    val coverResId: Int? = null,
    val coverPath: String? = null,
    val duration: Int = 0,
    val isLiked: Boolean = false,
    val isLocal: Boolean = false,
    val lastPlayedAt: Long = 0L,
    val uploadedBy: Int = 0
) : Parcelable {
    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        title = parcel.readString() ?: "",
        artist = parcel.readString() ?: "",
        filePath = parcel.readString(),
        resId = parcel.readValue(Int::class.java.classLoader) as? Int,
        coverResId = parcel.readInt(),
        coverPath = parcel.readString(),
        duration = parcel.readInt(),
        isLiked = parcel.readByte() != 0.toByte(),
        isLocal = parcel.readByte() != 0.toByte(),
        lastPlayedAt = parcel.readLong(),
        uploadedBy = parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(title)
        parcel.writeString(artist)
        parcel.writeString(filePath)
        parcel.writeValue(resId)
        parcel.writeValue(coverResId)
        parcel.writeString(coverPath)
        parcel.writeInt(duration)
        parcel.writeByte(if (isLiked) 1 else 0)
        parcel.writeByte(if (isLocal) 1 else 0)
        parcel.writeLong(lastPlayedAt)
        parcel.writeInt(uploadedBy)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Song> {
        override fun createFromParcel(parcel: Parcel): Song = Song(parcel)
        override fun newArray(size: Int): Array<Song?> = arrayOfNulls(size)
    }
}