package com.tubes.purry.ui.player

object NowPlayingManager {
    var viewModel: NowPlayingViewModel? = null
        private set

    fun setViewModel(vm: NowPlayingViewModel) {
        viewModel = vm
    }

    fun clearViewModel() {
        viewModel = null
    }
}