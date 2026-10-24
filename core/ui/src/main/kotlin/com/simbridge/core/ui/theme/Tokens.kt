package com.simbridge.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 20.dp
}

/** Minimum touch target for primary call actions (answer / reject / hang up). */
val LargeTouchTarget = 72.dp

enum class StatusTone { Positive, Warning, Negative, Info, Neutral }

@Immutable
data class ToneColors(val container: Color, val content: Color, val accent: Color)

@Composable
fun StatusTone.colors(): ToneColors {
    val dark = isSystemInDarkTheme()
    return when (this) {
        StatusTone.Positive -> if (dark) ToneColors(Color(0xFF103D27), Color(0xFFB6F2CF), Color(0xFF4CD08A))
        else ToneColors(Color(0xFFDDF6E8), Color(0xFF0B4A2A), Color(0xFF1E9E5A))
        StatusTone.Warning -> if (dark) ToneColors(Color(0xFF42300A), Color(0xFFFFE2A6), Color(0xFFF2B53A))
        else ToneColors(Color(0xFFFFF1D2), Color(0xFF5A3D00), Color(0xFFC98A00))
        StatusTone.Negative -> if (dark) ToneColors(Color(0xFF4A1414), Color(0xFFFFD6D3), Color(0xFFFF8A80))
        else ToneColors(Color(0xFFFDE2E1), Color(0xFF6B0F0F), Color(0xFFC62828))
        StatusTone.Info -> if (dark) ToneColors(Color(0xFF16265C), Color(0xFFDDE4FF), Color(0xFF8EA6FF))
        else ToneColors(Color(0xFFE3E9FF), Color(0xFF0D2470), Color(0xFF2350E6))
        StatusTone.Neutral -> if (dark) ToneColors(Color(0xFF262B37), Color(0xFFD5D9E4), Color(0xFF9AA1B2))
        else ToneColors(Color(0xFFEDEFF5), Color(0xFF353B4A), Color(0xFF6B7285))
    }
}
