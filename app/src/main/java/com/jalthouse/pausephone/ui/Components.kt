package com.jalthouse.pausephone.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

/** The brush ensō with a pause sign. */
@Composable
fun Enso(size: Dp, color: Color = Ink.ink) {
    val paths = remember {
        val enso = PathParser().parsePathString(ENSO_PATH).toPath().apply { fillType = PathFillType.EvenOdd }
        val bars = PathParser().parsePathString(PAUSE_PATH).toPath()
        enso to bars
    }
    Canvas(
        Modifier
            .size(size)
            .semantics { contentDescription = "[pause]" },
    ) {
        val k = this.size.minDimension / 200f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(paths.first, color)
            drawPath(paths.second, color)
        }
    }
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
        colors = ButtonDefaults.buttonColors(
            containerColor = if (dark) Ink.mist else Ink.ink,
            contentColor = if (dark) Ink.night else Ink.paper,
        ),
    ) { Text(text, style = Type.button) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, dark: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, if (dark) Ink.mistLine else Ink.ink),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (dark) Ink.mist else Ink.ink),
    ) { Text(text, style = Type.button) }
}

/** A labelled slider that moves through a fixed list of [steps]. */
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
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label, style = Type.heading, color = Ink.ink, modifier = Modifier.weight(1f))
            Text(format(value), style = Type.body.copy(fontSize = 15.sp), color = Ink.ink)
        }
        Text(hint, style = Type.small, color = Ink.muted, modifier = Modifier.padding(top = 2.dp))
        Slider(
            value = index.toFloat(),
            onValueChange = { v ->
                val i = v.roundToInt().coerceIn(0, steps.lastIndex)
                if (i != index) onChange(steps[i])
            },
            valueRange = 0f..steps.lastIndex.toFloat(),
            steps = (steps.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Ink.ink,
                activeTrackColor = Ink.ink,
                inactiveTrackColor = Ink.track,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = Ink.line)
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
