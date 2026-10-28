package com.keepwallet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A slim one-line text field for the entry form: icon, hint, text, and a clear button once there is text. */
@Composable
internal fun CompactField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  imeAction: ImeAction = ImeAction.Next,
  onNext: () -> Unit = {},
  onClear: (() -> Unit)? = null,
  tag: String = ""
) {
  val shape = RoundedCornerShape(12.dp)
  Row(
    modifier
      .height(44.dp)
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(10.dp))
    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
      BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onNext() }),
        modifier = Modifier.fillMaxWidth().then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
        decorationBox = { inner ->
          if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          inner()
        }
      )
    }
    if (onClear != null && value.isNotEmpty()) {
      Icon(
        Icons.Rounded.Close,
        contentDescription = "Clear",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClear)
      )
    }
  }
}

/** A tappable value (the date, the account, the category) in a slim bordered box. */
@Composable
internal fun PickerChip(
  onClick: () -> Unit,
  text: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  iconContent: (@Composable () -> Unit)? = null,
  trailing: String? = null,
  trailingArrow: Boolean = false,
  height: Dp = 42.dp
) {
  val shape = RoundedCornerShape(12.dp)
  Row(
    modifier
      .height(height)
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    if (iconContent != null) iconContent()
    else if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    if (trailingArrow) Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
  }
}

/** Plain clickable text, for actions that should not look like buttons. */
@Composable
internal fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
  Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = color,
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .clickable(role = Role.Button, onClick = onClick)
      .padding(vertical = 6.dp)
  )
}
