package com.keepwallet.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Premium physics and timings: fluid deceleration curve, bouncy tactile feedback for taps,
 * and snappy transitions for lag-free response.
 */
object Motion {
  /** Fast to start, long soft landing (ease-out cubic / quint). */
  val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

  /** For things leaving: responsive and clean exit. */
  val Exit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

  /** Snappy spring for tap scale & toggle animations. */
  fun <T> snappySpring() = spring<T>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
  )

  /** Smooth spring for cards and layout transitions. */
  fun <T> smoothSpring() = spring<T>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
  )

  const val Short = 160
  const val Medium = 260
  const val Page = 320
}
