package com.jalthouse.pausephone.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.jalthouse.pausephone.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Your brush ensō with the pause sign, in any ink colour. */
@Composable
fun Enso(size: Dp, color: Color = Ink.ink) {
    Image(
        painter = painterResource(R.drawable.enso_brush),
        contentDescription = "[pause]",
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(size),
    )
}

@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 0.dp, top = 8.dp, bottom = 8.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink.ink)
        }
        Text(title, style = Type.title.copy(fontSize = 26.sp), color = Ink.ink)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Ink.panel, RoundedCornerShape(18.dp))
            .border(1.dp, Ink.line, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, dark: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (dark) Ink.mist else Ink.ink,
            contentColor = if (dark) Ink.night else Ink.paper,
        ),
    ) { Text(text, style = Type.button, maxLines = 1) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, dark: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        border = BorderStroke(1.dp, if (dark) Ink.mistLine else Ink.ink),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (dark) Ink.mist else Ink.ink),
    ) { Text(text, style = Type.button, maxLines = 1) }
}

/**
 * A labelled row of dots, one per step: tap a dot, slide along them, or use − and +.
 * The dots sit inset from the screen edge so the first and last are easy to reach.
 */
@Composable
fun <T> StepSlider(
    label: String,
    hint: String,
    steps: List<T>,
    value: T,
    format: (T) -> String,
    onChange: (T) -> Unit,
) {
    val index = steps.indexOf(value).coerceAtLeast(0)
    val current by rememberUpdatedState(index)
    val change by rememberUpdatedState(onChange)
    fun pickAt(x: Float, width: Float, pad: Float) {
        val span = (width - 2 * pad).coerceAtLeast(1f)
        val i = (((x - pad) / span) * steps.lastIndex).roundToInt().coerceIn(0, steps.lastIndex)
        if (i != current) change(steps[i])
    }
    val dotColor = Ink.ink
    val restColor = Ink.track
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label, style = Type.heading, color = Ink.ink, modifier = Modifier.weight(1f))
            Text(format(value), style = Type.body.copy(fontSize = 15.sp), color = Ink.ink)
        }
        Text(hint, style = Type.small, color = Ink.muted, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", "Less") { if (current > 0) change(steps[current - 1]) }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { stateDescription = format(value) }
                    // A tap picks a dot. Dragging sideways moves along them, but only once the
                    // finger clearly moves sideways, so scrolling the page never changes a setting.
                    .pointerInput(steps.size) {
                        detectTapGestures { p -> pickAt(p.x, size.width.toFloat(), 10.dp.toPx()) }
                    }
                    .pointerInput(steps.size) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            pickAt(change.position.x, size.width.toFloat(), 10.dp.toPx())
                        }
                    },
            ) {
                val pad = 10.dp.toPx()
                val span = size.width - 2 * pad
                val cy = size.height / 2f
                val n = steps.size
                for (i in 0 until n) {
                    val cx = pad + if (n > 1) span * i / (n - 1) else span / 2f
                    when {
                        i == index -> drawCircle(dotColor, radius = 8.dp.toPx(), center = Offset(cx, cy))
                        i < index -> drawCircle(dotColor, radius = 3.dp.toPx(), center = Offset(cx, cy))
                        else -> drawCircle(restColor, radius = 3.dp.toPx(), center = Offset(cx, cy))
                    }
                }
            }
            StepButton("+", "More") { if (current < steps.lastIndex) change(steps[current + 1]) }
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = Ink.line)
    }
}

@Composable
private fun StepButton(symbol: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = Type.body.copy(fontSize = 22.sp, fontWeight = FontWeight.Light), color = Ink.ink)
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = Type.heading, color = Ink.ink)
                Text(subtitle, style = Type.small, color = Ink.muted)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Ink.ink,
                    checkedThumbColor = Ink.paper,
                    uncheckedTrackColor = Ink.track,
                    uncheckedThumbColor = Ink.paper,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

/** A letter tile standing in for an app or site icon. */
@Composable
fun LetterTile(label: String) {
    val letters = label.filter { it.isLetterOrDigit() }.take(2)
        .replaceFirstChar { it.uppercase() }
        .let { if (it.length == 2) it[0] + it[1].lowercase() else it }
    Box(
        Modifier.size(40.dp).background(Ink.tile, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letters, style = Type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = Ink.ink)
    }
}

@Composable
fun ListRow(
    title: String,
    subtitle: String?,
    leading: @Composable () -> Unit,
    onClick: (() -> Unit)?,
    trailingText: String? = null,
    divider: Boolean = true,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            leading()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = Type.body, color = Ink.ink)
                if (subtitle != null) Text(subtitle, style = Type.small, color = Ink.muted)
            }
            if (trailingText != null) Text(trailingText, style = Type.body.copy(fontSize = 14.sp), color = Ink.muted)
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Ink.muted)
            }
        }
        if (divider) HorizontalDivider(color = Ink.line)
    }
}

fun minutesText(m: Int): String = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60} min"
}
