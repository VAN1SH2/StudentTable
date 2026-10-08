package ru.sfu.student.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudentColors = lightColorScheme(
    primary = Color(0xFF7051C7), onPrimary = Color.White,
    primaryContainer = Color(0xFFF1ECFC), onPrimaryContainer = Color(0xFF4F3394),
    secondary = Color(0xFF7051C7), secondaryContainer = Color(0xFFF1ECFC), onSecondaryContainer = Color(0xFF4F3394),
    background = Color.White, onBackground = Color(0xFF202024),
    surface = Color.White, onSurface = Color(0xFF202024), surfaceVariant = Color(0xFFF6F6F8), onSurfaceVariant = Color(0xFF777780),
    surfaceContainer = Color(0xFFF7F7F9), surfaceContainerLow = Color(0xFFFAFAFC), surfaceContainerHigh = Color(0xFFF4F4F6),
    outline = Color(0xFFDDDEE4), outlineVariant = Color(0xFFEEEEF2), error = Color(0xFFC53B45),
    errorContainer = Color(0xFFFFF1F2), onErrorContainer = Color(0xFFA72F3A))

object TaskColors {
    val pendingContainer = Color(0xFFEEF8F0)
    val onPendingContainer = Color(0xFF25663B)
    val lessonPendingContainer = Color(0xFFD5EEDC)
    val lessonPendingOutline = Color(0xFFA8CCB2)
}

@Composable fun StudentTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StudentColors, content = content)
}
