package com.suitanglu.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Green10 = Color(0xFF0F2D26)
private val Green20 = Color(0xFF1B6E5F)
private val Green40 = Color(0xFF1F8A73)
private val Green80 = Color(0xFF7FDCC4)
private val Green90 = Color(0xFFB6F2E0)
private val Teal40 = Color(0xFF3D6A6A)
private val Teal80 = Color(0xFFB2ECEA)
private val ErrorRed40 = Color(0xFFBA1A1A)
private val ErrorRed80 = Color(0xFFFFB4AB)

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal80,
    onSecondaryContainer = Color(0xFF00201F),
    error = ErrorRed40,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Color(0xFF00382D),
    primaryContainer = Green20,
    onPrimaryContainer = Green90,
    secondary = Teal80,
    onSecondary = Color(0xFF003735),
    secondaryContainer = Teal40,
    onSecondaryContainer = Teal80,
    error = ErrorRed80,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = ErrorRed80,
)

/** 品牌绿强调色：进度条、录音圆环、标记点等品牌元素固定使用，不随 dynamic color 变化 */
@Composable
fun BrandAccent(): Color = if (isSystemInDarkTheme()) Color(0xFF7FDCC4) else Color(0xFF1F8A73)

/** 数字等宽（计时器、时间标签不左右跳动） */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")

private val RecordShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 34.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun RecordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, shapes = RecordShapes, content = content)
}
