import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class LikedSong(
    @PrimaryKey val id: Int,
    val title: String,
    val artist: String,
    val coverResId: Int
)
