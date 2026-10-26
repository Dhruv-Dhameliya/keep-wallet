package com.keepwallet.app.data

const val PACK_CLASSIC = "Classic"
const val PACK_SEASONAL = "Seasonal"

/**
 * A ready-made look: an accent colour, a second colour for highlights and a background, applied together with one
 * tap in Settings. Accent and background can still be changed one by one afterwards.
 */
enum class AppPalette(
  val label: String,
  val accent: AccentColor,
  /** ARGB of the highlight colour used for tertiary containers, chart accents and badges. */
  val tertiary: Long,
  val background: BackgroundMode,
  /** "Classic" looks are always on offer; "Seasonal" packs are themed and suggested around their time of year. */
  val pack: String = PACK_CLASSIC,
  /** A seed colour of its own, for packs whose colour is not one of the standard accents. */
  val seed: Long? = null
) {
  MONO("Mono", AccentColor.GRAPHITE, 0xFF9AA4AE, BackgroundMode.SYSTEM),
  FOREST("Forest", AccentColor.TEAL, 0xFFF5B544, BackgroundMode.LIGHT),
  OCEAN("Ocean", AccentColor.BLUE, 0xFF06B6D4, BackgroundMode.LIGHT),
  MIDNIGHT("Midnight", AccentColor.VIOLET, 0xFFF472B6, BackgroundMode.DARK),
  SUNSET("Sunset", AccentColor.TERRACOTTA, 0xFFF59E0B, BackgroundMode.LIGHT),
  BERRY("Berry", AccentColor.ROSE, 0xFF8B5CF6, BackgroundMode.WHITE),
  MEADOW("Meadow", AccentColor.GREEN, 0xFFEAB308, BackgroundMode.LIGHT),
  EMBER("Ember", AccentColor.AMBER, 0xFF14B8A6, BackgroundMode.DARK),
  AURORA("Aurora", AccentColor.VIOLET, 0xFF22D3EE, BackgroundMode.BLACK),
  CANDY("Candy", AccentColor.ROSE, 0xFF22D3EE, BackgroundMode.LIGHT),
  NOIR("Noir", AccentColor.GRAPHITE, 0xFFF5B544, BackgroundMode.BLACK),

  // Seasonal packs
  WINTER("Winter", AccentColor.BLUE, 0xFF93C5FD, BackgroundMode.DARK, PACK_SEASONAL, 0xFF3B82F6),
  HOLI("Holi", AccentColor.ROSE, 0xFF22C55E, BackgroundMode.LIGHT, PACK_SEASONAL, 0xFFDB2777),
  SPRING("Spring", AccentColor.GREEN, 0xFFF472B6, BackgroundMode.LIGHT, PACK_SEASONAL, 0xFF22A06B),
  SUMMER("Summer", AccentColor.AMBER, 0xFF06B6D4, BackgroundMode.LIGHT, PACK_SEASONAL, 0xFFF59E0B),
  MONSOON("Monsoon", AccentColor.TEAL, 0xFF60A5FA, BackgroundMode.DARK, PACK_SEASONAL, 0xFF0F766E),
  DIWALI("Diwali", AccentColor.AMBER, 0xFFE11D48, BackgroundMode.LIGHT, PACK_SEASONAL, 0xFFD97706),
  AUTUMN("Autumn", AccentColor.TERRACOTTA, 0xFFCA8A04, BackgroundMode.LIGHT, PACK_SEASONAL, 0xFFB45309),
  HALLOWEEN("Halloween", AccentColor.AMBER, 0xFF8B5CF6, BackgroundMode.BLACK, PACK_SEASONAL, 0xFFEA580C),
  CHRISTMAS("Christmas", AccentColor.GREEN, 0xFFDC2626, BackgroundMode.LIGHT, PACK_SEASONAL, 0xFF166534);

  /** The colour the theme is built from. */
  val seedArgb: Long get() = seed ?: accent.argb

  companion object {
    val classic: List<AppPalette> get() = entries.filter { it.pack == PACK_CLASSIC }
    val seasonal: List<AppPalette> get() = entries.filter { it.pack == PACK_SEASONAL }

    /** The seasonal pack that fits the given month (1-12). */
    fun suggestedFor(month: Int): AppPalette = when (month) {
      1, 2 -> WINTER
      3 -> HOLI
      4, 5 -> SPRING
      6 -> SUMMER
      7, 8, 9 -> MONSOON
      10 -> DIWALI
      11 -> AUTUMN
      else -> CHRISTMAS
    }
  }
}
