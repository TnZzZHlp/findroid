package dev.jdtech.jellyfin.presentation.player

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import java.lang.IllegalStateException

class SubtitleDelayDialogFragment(private val viewModel: PlayerViewModel) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = activity ?: throw IllegalStateException("Activity cannot be null")
        val dialog =
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.subtitle_delay)
                .setMessage(getString(R.string.subtitle_delay_value, viewModel.subtitleDelayMs))
                .setNegativeButton(R.string.subtitle_earlier, null)
                .setNeutralButton(R.string.reset, null)
                .setPositiveButton(R.string.subtitle_later, null)
                .create()

        dialog.setOnShowListener {
            fun updateMessage() {
                dialog.setMessage(
                    getString(R.string.subtitle_delay_value, viewModel.subtitleDelayMs)
                )
            }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                viewModel.adjustSubtitleDelay(-250L)
                updateMessage()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                viewModel.resetSubtitleDelay()
                updateMessage()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                viewModel.adjustSubtitleDelay(250L)
                updateMessage()
            }
        }
        return dialog
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
