package com.tubes.purry.data.model

data class RecommendationItem(
    val id: String,
    val title: String,
    val description: String,
    val imageRes: Int,
    val type: RecommendationType
)

enum class RecommendationType {
    DAILY_MIX,
    RECENTLY_PLAYED_MIX,
    LIKED_SONGS_MIX,
    DISCOVERY_MIX
}