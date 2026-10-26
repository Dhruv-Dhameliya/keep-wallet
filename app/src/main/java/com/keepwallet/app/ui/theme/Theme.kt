package com.keepwallet.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepwallet.app.R
import com.keepwallet.app.data.BackgroundMode
import com.keepwallet.app.data.ColorStyle

/** Semantic colours that Material's scheme has no slot for. */
@Immutable
data class ExtraColors(
  val income: Color,
  val incomeContainer: Color,
  val warning: Color,
  val warningContainer: Color,
  /** The balance card's two gradient stops; always rich enough for white text. */
  val cardStart: Color,
  val cardEnd: Color,
  /** The palette's second colour, used for lively accents beside the main one. */
  val highlight: Color,
  val highlightContainer: Color,
  val onHighlightContainer: Color
)

val LocalExtraColors = staticCompositionLocalOf {
  ExtraColors(
    Color(0xFF15803D), Color(0xFFDCFCE7), Color(0xFFC2410C), Color(0xFFFFEDD5),
    Color(0xFF16655E), Color(0xFF0B3A36),
    Color(0xFFF5B544), Color(0xFFFDEBC8), Color(0xFF5A3C00)
  )
}

val MaterialTheme.extra: ExtraColors
  @Composable get() = LocalExtraColors.current

/** True for greys and near-greys (the black-and-white look). */
private fun isGrey(color: Color): Boolean = maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue) < 0.08f

fun shiftHue(color: Color, degrees: Float): Color {
  val hsv = FloatArray(3)
  android.graphics.Color.colorToHSV(color.toArgb(), hsv)
  hsv[0] = (hsv[0] + degrees + 360f) % 360f
  return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun ColorStyle.palette(): PaletteStyle = when (this) {
  ColorStyle.BALANCED -> PaletteStyle.TonalSpot
  ColorStyle.VIBRANT -> PaletteStyle.Vibrant
  ColorStyle.FAITHFUL -> PaletteStyle.Fidelity
  ColorStyle.NEUTRAL -> PaletteStyle.Neutral
}

private val lightExtra = ExtraColors(
  Color(0xFF15803D), Color(0xFFDCFCE7), Color(0xFFC2410C), Color(0xFFFFEDD5), Color.Unspecified, Color.Unspecified,
  Color.Unspecified, Color.Unspecified, Color.Unspecified
)
private val darkExtra = ExtraColors(
  Color(0xFF4ADE80), Color(0xFF14391F), Color(0xFFFB923C), Color(0xFF45240F), Color.Unspecified, Color.Unspecified,
  Color.Unspecified, Color.Unspecified, Color.Unspecified
)

/** Inter, a variable font bundled with the app: crisp numerals and an even rhythm, the same on every phone. */
private fun inter(weight: FontWeight) = Font(R.font.inter_variable, weight, variationSettings = FontVariation.Settings(weight, FontStyle.Normal))

val InterFamily = FontFamily(inter(FontWeight.Normal), inter(FontWeight.Medium), inter(FontWeight.SemiBold), inter(FontWeight.Bold))

/**
 * Material 3's type scale set in Inter. Digits are tabular so columns of amounts line up, headings are a touch
 * tighter and heavier, and body text keeps Material's sizes and line heights.
 */
private val AppTypography = Typography().let { base ->
  fun TextStyle.inter(weight: FontWeight? = null, tracking: Float? = null) = copy(
    fontFamily = InterFamily,
    fontWeight = weight ?: fontWeight,
    letterSpacing = tracking?.sp ?: letterSpacing,
    fontFeatureSettings = "tnum"
  )
  base.copy(
    displayLarge = base.displayLarge.inter(FontWeight.SemiBold, -1.0f),
    displayMedium = base.displayMedium.inter(FontWeight.SemiBold, -0.8f),
    displaySmall = base.displaySmall.inter(FontWeight.SemiBold, -0.6f),
    headlineLarge = base.headlineLarge.inter(FontWeight.SemiBold, -0.5f),
    headlineMedium = base.headlineMedium.inter(FontWeight.SemiBold, -0.4f),
    headlineSmall = base.headlineSmall.inter(FontWeight.SemiBold, -0.3f),
    titleLarge = base.titleLarge.inter(FontWeight.SemiBold, -0.2f),
    titleMedium = base.titleMedium.inter(FontWeight.SemiBold, -0.1f),
    titleSmall = base.titleSmall.inter(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.inter(),
    bodyMedium = base.bodyMedium.inter(),
    bodySmall = base.bodySmall.inter(),
    labelLarge = base.labelLarge.inter(FontWeight.SemiBold),
    labelMedium = base.labelMedium.inter(FontWeight.Medium),
    labelSmall = base.labelSmall.inter(FontWeight.Medium)
  )
}

/** A softer shape scale than Material's default: cards and sheets are rounder, small parts stay crisp. */
private val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(20.dp),
  extraLarge = RoundedCornerShape(28.dp)
)

/**
 * The calm, near-neutral surface ladder used with the generated accent colours: the accent only whispers in the
 * backgrounds (a few percent), so cards, fields and sheets look clean and expensive in both light and dark while
 * buttons, progress and highlights keep the full colour.
 */
private fun ColorScheme.premium(dark: Boolean, neutral: Boolean = false): ColorScheme {
  val accent = primary
  fun mix(base: Long, amount: Float) = lerp(Color(base), accent, amount)
  val base = if (dark) copy(
    background = mix(0xFF0C0C0E, 0.045f),
    surface = mix(0xFF111113, 0.05f),
    surfaceDim = mix(0xFF0C0C0E, 0.045f),
    surfaceBright = mix(0xFF2B2B2F, 0.06f),
    surfaceContainerLowest = mix(0xFF09090B, 0.04f),
    surfaceContainerLow = mix(0xFF151517, 0.05f),
    surfaceContainer = mix(0xFF1B1B1E, 0.055f),
    surfaceContainerHigh = mix(0xFF222225, 0.06f),
    surfaceContainerHighest = mix(0xFF2A2A2E, 0.065f),
    outline = mix(0xFF4D4D53, 0.05f),
    outlineVariant = mix(0xFF2B2B2F, 0.06f),
    onBackground = Color(0xFFEDEFF1),
    onSurface = Color(0xFFEDEFF1),
    onSurfaceVariant = Color(0xFFA7AEB5)
  ) else copy(
    background = mix(0xFFF4F4F5, 0.035f),
    surface = Color(0xFFFCFCFD),
    surfaceDim = mix(0xFFE3E3E6, 0.04f),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFEFEFF),
    surfaceContainer = mix(0xFFEFEFF1, 0.045f),
    surfaceContainerHigh = mix(0xFFE9E9EC, 0.055f),
    surfaceContainerHighest = mix(0xFFE2E2E6, 0.065f),
    outline = mix(0xFFBBBBC1, 0.05f),
    outlineVariant = mix(0xFFE3E3E6, 0.05f),
    onBackground = Color(0xFF151517),
    onSurface = Color(0xFF151517),
    onSurfaceVariant = Color(0xFF59616A)
  )
  // A grey accent means the black-and-white look: real black (or white) buttons and bars, not mid-grey ones.
  return if (!neutral) base else if (dark) base.copy(
    primary = Color(0xFFF2F2F4), onPrimary = Color(0xFF121214),
    primaryContainer = Color(0xFF2A2A2E), onPrimaryContainer = Color(0xFFF2F2F4),
    secondary = Color(0xFFC9C9CE), onSecondary = Color(0xFF121214),
    secondaryContainer = Color(0xFF2E2E33), onSecondaryContainer = Color(0xFFEDEDEF),
    tertiaryContainer = Color(0xFF2E2E33), onTertiaryContainer = Color(0xFFEDEDEF),
    surfaceTint = Color(0xFFF2F2F4)
  ) else base.copy(
    primary = Color(0xFF141416), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E8EC), onPrimaryContainer = Color(0xFF141416),
    secondary = Color(0xFF3A3A40), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E6EA), onSecondaryContainer = Color(0xFF1B1B1E),
    tertiaryContainer = Color(0xFFE6E6EA), onTertiaryContainer = Color(0xFF1B1B1E),
    surfaceTint = Color(0xFF141416)
  )
}

/** Pure white keeps the surface ladder in plain greys instead of tinted ones. */
private fun ColorScheme.pureWhite(): ColorScheme = copy(
  background = Color.White, surface = Color.White, surfaceBright = Color.White, surfaceDim = Color(0xFFE9EBEB),
  surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F8F8), surfaceContainer = Color(0xFFF1F3F3),
  surfaceContainerHigh = Color(0xFFEAEDED), surfaceContainerHighest = Color(0xFFE3E7E7)
)

/**
 * The app's Material 3 theme. The scheme comes from Material Color Utilities (the same tonal-palette maths Android
 * itself uses), seeded with the accent colour and, optionally, a second highlight colour for the tertiary role.
 * [systemColors] switches to the wallpaper-derived Material You scheme on Android 12+.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeepWalletTheme(
  accent: Color,
  highlight: Color?,
  background: BackgroundMode,
  colorStyle: ColorStyle = ColorStyle.BALANCED,
  systemColors: Boolean = false,
  content: @Composable () -> Unit
) {
  val systemDark = isSystemInDarkTheme()
  val dark = when (background) {
    BackgroundMode.SYSTEM -> systemDark
    BackgroundMode.DARK, BackgroundMode.BLACK -> true
    BackgroundMode.LIGHT, BackgroundMode.WHITE -> false
  }
  val context = LocalContext.current
  val colors = remember(accent, highlight, background, dark, colorStyle, systemColors) {
    val base = if (systemColors && Build.VERSION.SDK_INT >= 31) {
      if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
      dynamicColorScheme(
        primary = accent,
        isDark = dark,
        isAmoled = background == BackgroundMode.BLACK,
        tertiary = highlight,
        style = colorStyle.palette()
      )
    }
    when {
      background == BackgroundMode.WHITE -> base.pureWhite()
      systemColors && Build.VERSION.SDK_INT >= 31 -> base
      background == BackgroundMode.BLACK -> base
      else -> base.premium(dark, neutral = isGrey(highlight?.let { accent } ?: accent))
    }
  }
  val extra = remember(colors, dark) {
    val start = if (dark) colors.primaryContainer else lerp(colors.primary, Color.Black, 0.22f)
    (if (dark) darkExtra else lightExtra).copy(
      cardStart = start,
      cardEnd = lerp(start, Color.Black, if (dark) 0.45f else 0.5f),
      highlight = colors.tertiary,
      highlightContainer = colors.tertiaryContainer,
      onHighlightContainer = colors.onTertiaryContainer
    )
  }
  CompositionLocalProvider(LocalExtraColors provides extra) {
    MaterialExpressiveTheme(
      colorScheme = colors,
      typography = AppTypography,
      shapes = AppShapes,
      motionScheme = MotionScheme.standard(),
      content = content
    )
  }
}

/** True when the current theme is dark, for the few places that need to adapt tints. */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminanceValue() < 0.4f

private fun Color.luminanceValue(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
