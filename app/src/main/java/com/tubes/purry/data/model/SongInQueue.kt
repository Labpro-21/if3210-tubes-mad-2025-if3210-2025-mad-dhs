package com.tubes.purry.data.model


data class SongInQueue(
    val song: Song,
    val fromManualQueue: Boolean = false
)
