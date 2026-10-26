package com.keepwallet.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * One set of timings and curves for the whole app, so every transition feels like the same hand made it. The main
 * curve starts fast and settles slowly, the way iOS moves things: quick to respond, gentle to arrive.
 */
object Motion {
  /** Fast to start, long soft landing (an "ease-out-quint"-like curve). */
  val Standard: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

  /** For things leaving: a little quicker, no lingering. */
  val Exit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

  const val Short = 160
  const val Medium = 260
  const val Page = 360
}
