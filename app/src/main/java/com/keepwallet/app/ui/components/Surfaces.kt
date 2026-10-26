package com.keepwallet.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer

/** A filled panel behind some content (toggles, keys, fields, chips): the given colour in the given shape. */
@Composable
fun Modifier.panel(shape: Shape, base: Color, @Suppress("UNUSED_PARAMETER") strength: Float = 1f): Modifier =
  this.clip(shape).background(base)

/** Shrinks slightly while pressed and springs back: quick tactile feedback for anything tappable. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, scale: Float = 0.97f): Modifier {
  val pressed by source.collectIsPressedAsState()
  val factor by animateFloatAsState(if (pressed) scale else 1f, spring(dampingRatio = 0.7f, stiffness = 900f), label = "press")
  return this.graphicsLayer {
    scaleX = factor
    scaleY = factor
  }
}

/**
 * Page background. Everything on the page inherits the right text colour for it; without this, text outside a
 * Surface falls back to black, which disappears on the dark and pure-black looks.
 */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
  }
}
