package com.example.locallens.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(16.dp)
val ButtonShape = RoundedCornerShape(8.dp)
val PillShape = RoundedCornerShape(160.dp)

/** Canvas gray with a 1px hairline grid, like graph paper. */
fun Modifier.paperGrid(spacing: Int = 36): Modifier = this
  .background(Canvas)
  .drawBehind {
    val step = spacing.dp.toPx()
    val stroke = 1.dp.toPx()
    var x = 0f
    while (x < size.width) {
      drawLine(Hairline, Offset(x, 0f), Offset(x, size.height), stroke)
      x += step
    }
    var y = 0f
    while (y < size.height) {
      drawLine(Hairline, Offset(0f, y), Offset(size.width, y), stroke)
      y += step
    }
  }

/** The one filled button style: ink fill, white tracked mono label. */
@Composable
fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Text(
    text.uppercase(),
    style = Label,
    color = Paper,
    textAlign = TextAlign.Center,
    modifier = modifier
      .background(Ink, ButtonShape)
      .clickable(onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 12.dp),
  )
}

/** Shared layout of the setup steps: eyebrow, headline, body content, then the actions pinned to the bottom. */
@Composable
fun StepLayout(
  eyebrow: String,
  headline: String,
  modifier: Modifier = Modifier,
  // Turn off when the content already fills the space with its own weighted child.
  pushActionsDown: Boolean = true,
  actions: @Composable ColumnScope.() -> Unit = {},
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
    horizontalAlignment = Alignment.Start,
  ) {
    EyebrowText(eyebrow)
    Text(
      headline,
      style = MaterialTheme.typography.headlineSmall.copy(fontSize = 32.sp, lineHeight = 36.sp),
      color = Ink,
    )
    content()
    if (pushActionsDown) Spacer(Modifier.weight(1f))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
  }
}

/** A mono label and value separated by hairlines, like a spec sheet. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
  Row(
    modifier.fillMaxWidth().drawBehind {
      drawLine(Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }.padding(vertical = 14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    EyebrowText(label)
    EyebrowText(value)
  }
}

/** A line of body text led by a small green status dot. */
@Composable
fun BulletRow(text: String, modifier: Modifier = Modifier) {
  Row(
    modifier.fillMaxWidth().drawBehind {
      drawLine(Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }.padding(vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(Modifier.size(8.dp).background(SignalGreen, CircleShape))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Ink)
  }
}

/** Ghost action: tracked mono text with no fill or border. */
@Composable
fun GhostLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Text(
    text.uppercase(),
    style = Label,
    color = Ink,
    modifier = modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
  )
}

@Composable
fun EyebrowText(text: String, modifier: Modifier = Modifier) {
  Text(text.uppercase(), style = Label, color = Ink, modifier = modifier)
}

/** Pill-shaped search field with a small ink action button at the right end. */
@Composable
fun PillSearchBar(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  onSearch: () -> Unit = {},
  enabled: Boolean = true,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(Canvas, PillShape)
      .border(1.dp, Hairline, PillShape)
      .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = Modifier.weight(1f),
      enabled = enabled,
      singleLine = true,
      textStyle = LabelLarge.copy(color = Ink),
      cursorBrush = SolidColor(Ink),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { onSearch() }),
      decorationBox = { inner ->
        if (value.isEmpty()) Text(placeholder.uppercase(), style = LabelLarge, color = Ink.copy(alpha = 0.55f))
        inner()
      },
    )
    InkButton("Search", onClick = onSearch)
  }
}
