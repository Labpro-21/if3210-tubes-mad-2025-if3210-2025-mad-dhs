//package com.tubes.purry.ui.player
//
//import android.graphics.Color
//import android.graphics.drawable.BitmapDrawable
//import android.graphics.drawable.Drawable
//import android.graphics.drawable.GradientDrawable
//import android.media.MediaPlayer
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import androidx.fragment.app.Fragment
//import androidx.palette.graphics.Palette
//import com.tubes.purry.databinding.c.iFragmentNowPlayingBinding
//
//class NowPlayingFragment : Fragment() {
//
//    private var _binding: FragmentNowPlayingBinding? = null
//    private val binding get() = _binding!!
//    private var mediaPlayer: MediaPlayer? = null
//
//    private fun setDynamicBackgroundFromAlbum(coverDrawable: Drawable) {
//        val bitmap = (coverDrawable as BitmapDrawable).bitmap
//
//        Palette.from(bitmap).generate { palette ->
//            val dominantColor = palette?.getDominantColor(Color.BLACK) ?: Color.BLACK
//            val gradientDrawable = GradientDrawable(
//                GradientDrawable.Orientation.TOP_BOTTOM,
//                intArrayOf(dominantColor, Color.BLACK) // bisa disesuaikan
//            )
//            binding.nowPlayingRoot.background = gradientDrawable
//        }
//    }
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        _binding = FragmentNowPlayingBinding.inflate(inflater, container, false)
//        return binding.root
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }
//}
