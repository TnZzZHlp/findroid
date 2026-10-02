package dev.jdtech.jellyfin.presentation.player

import android.app.Dialog
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.player.local.presentation.formatLabel

class VideoQualityDialogFragment(private val viewModel: PlayerViewModel) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val qualities = viewModel.availableVideoQualities
        val selectedIndex =
            qualities
                .indexOfFirst {
                    it.maxStreamingBitrate == viewModel.videoQuality.maxStreamingBitrate
                }
                .takeIf { it >= 0 } ?: 0
        return requireActivity().let { activity ->
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.select_video_quality)
                .setSingleChoiceItems(
                    qualities.map { it.formatLabel(activity) }.toTypedArray(),
                    selectedIndex,
                ) { dialog, which ->
                    viewModel.selectVideoQuality(qualities[which])
                    dialog.dismiss()
                }
                .create()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
