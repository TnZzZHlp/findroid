package dev.jdtech.jellyfin.presentation.player

import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.core.view.updatePadding

class SpeedBoostOverlay(container: View, private val pill: View) {
    init {
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                left = cutout.left,
                top = cutout.top,
                right = cutout.right,
                bottom = cutout.bottom,
            )
            insets
        }
        container.doOnAttach { ViewCompat.requestApplyInsets(it) }
    }

    fun show() {
        pill.animate().cancel()
        if (!pill.isVisible) {
            pill.alpha = 0f
            pill.scaleX = 0.92f
            pill.scaleY = 0.92f
            pill.isVisible = true
        }
        pill
            .animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction(null)
            .start()
    }

    fun hide() {
        if (!pill.isVisible) return
        pill.animate().cancel()
        pill
            .animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(140)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { pill.isVisible = false }
            .start()
    }
}
