package dev.jdtech.jellyfin.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val baseline = Typography()

val typography =
    Typography(
        displayLarge = baseline.displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = baseline.displayMedium.copy(fontWeight = FontWeight.Bold),
        displaySmall = baseline.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = baseline.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = baseline.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = baseline.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = baseline.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = baseline.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = baseline.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    )
