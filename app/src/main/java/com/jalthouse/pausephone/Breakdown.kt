package com.jalthouse.pausephone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jalthouse.pausephone.data.Store
import com.jalthouse.pausephone.data.Usage
import com.jalthouse.pausephone.data.durationText
import com.jalthouse.pausephone.ui.BackHeader
import com.jalthouse.pausephone.ui.Ink
import com.jalthouse.pausephone.ui.Panel
import com.jalthouse.pausephone.ui.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Where your time goes: today hour by hour, or the week day by day, by group and by app. */
@Composable
internal fun BreakdownScreen(back: () -> Unit) {
    val context = LocalContext.current
    var week by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Usage.Detail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selTime by remember { mutableStateOf<Int?>(null) }
    var selPick by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(week) {
        loading = true
        selTime = null
        selPick = null
        detail = withContext(Dispatchers.IO) { if (week) Usage.weekDetail(context) else Usage.todayDetail(context) }
        loading = false
    }
    val labels = remember { mutableMapOf<String, String>() }
    fun label(pkg: String) = labels.getOrPut(pkg) { Usage.label(context, pkg) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        BackHeader("Where your time goes", back)
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Choice(listOf("today" to "Today", "week" to "This week"), if (week) "week" else "today") { week = it == "week" }
            val d = detail
            if (d == null) {
                Text(
                    if (loading) "Looking at your phone's count…"
                    else "Turn on Usage access for [pause] to see this.",
                    style = Type.body,
                    color = Ink.muted,
                )
            } else {
                val s = d.summary
                // The headline numbers
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Hero(
                        if (week) durationText(s.msPerDay) else durationText(s.totalMs),
                        if (week) "a day on average" else "on screen today",
                        Modifier.weight(1f),
                    )
                    Hero(
                        "${if (week) s.pickupsPerDay else s.pickups}",
                        if (week) "pickups a day" else "pickups today",
                        Modifier.weight(1f),
                    )
                }
                if (!week && d.firstPickup != null) {
                    Text("Your first pickup today was at ${hhmm(d.firstPickup)}.", style = Type.body, color = Ink.ink)
                }
                if (week) {
                    Text("${durationText(s.totalMs)} in all over the last seven days.", style = Type.body, color = Ink.ink)
                }

                // Time, hour by hour or day by day
                val bucketName: (Int) -> String = { i ->
                    if (week) dayName(d.from + i * d.bucketMs) else "%02d:00–%02d:00".format(i, (i + 1) % 24)
                }
                val ticks: (Int) -> String? = { i ->
                    if (week) dayName(d.from + i * d.bucketMs).take(3) else if (i % 6 == 0) "%02d".format(i) else null
                }
                ChartCard(
                    title = "Time on screen",
                    readout = selTime?.let { "${bucketName(it)} · ${durationText(d.time[it])}" }
                        ?: "Tap a bar to see it",
                    values = d.time.map { it.toFloat() },
                    ticks = ticks,
                    selected = selTime,
                    onSelect = { selTime = it },
                    description = "Time on screen by ${if (week) "day" else "hour"}",
                )
                ChartCard(
                    title = "Pickups",
                    readout = selPick?.let { "${bucketName(it)} · ${d.pickups[it]} ${if (d.pickups[it] == 1) "pickup" else "pickups"}" }
                        ?: "Tap a bar to see it",
                    values = d.pickups.map { it.toFloat() },
                    ticks = ticks,
                    selected = selPick,
                    onSelect = { selPick = it },
                    description = "Pickups by ${if (week) "day" else "hour"}",
                )

                // By group
                val groups = Store.groups()
                if (groups.isNotEmpty()) {
                    val total = s.totalMs.coerceAtLeast(1L)
                    val byGroup = groups.map { g ->
                        g to g.members.sumOf { m -> s.perApp.find { it.first == m.id }?.second ?: 0L }
                    }.sortedByDescending { it.second }
                    Section("By group")
                    byGroup.forEach { (g, ms) ->
                        ShareRow(g.name, durationText(ms), ms.toFloat() / total, null)
                    }
                    val rest = total - byGroup.sumOf { it.second }
                    if (rest > 60_000L) ShareRow("Everything else", durationText(rest), rest.toFloat() / total, null)
                }

                // By app
                if (s.perApp.isNotEmpty()) {
                    val top = s.perApp.first().second.toFloat().coerceAtLeast(1f)
                    Section("By app")
                    s.perApp.take(15).forEach { (pkg, ms) ->
                        val opens = d.opens[pkg] ?: 0
                        val group = Store.groupOf(pkg)?.name
                        ShareRow(
                            label(pkg),
                            if (week) durationText(ms / s.days) + " a day" else durationText(ms),
                            ms / top,
                            listOfNotNull(
                                if (opens > 0) "opened ${if (opens == 1) "once" else "$opens times"}" else null,
                                group?.let { "in $it" },
                            ).joinToString(" · ").ifBlank { null },
                        )
                    }
                }
                Text(
                    "From your phone's own count. Nothing leaves your phone.",
                    style = Type.small,
                    color = Ink.muted,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

private fun hhmm(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5)

private fun dayName(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).dayOfWeek
    .getDisplayName(TextStyle.FULL, Locale.getDefault())

@Composable
private fun Hero(value: String, label: String, modifier: Modifier) {
    Panel(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = Type.stat.copy(fontSize = 28.sp), color = Ink.ink)
            Text(label, style = Type.small, color = Ink.muted)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = Type.heading, color = Ink.ink, modifier = Modifier.padding(top = 4.dp))
}

/** A thin single-series bar chart. Tap a bar to read it; no legend needed for one series. */
@Composable
private fun ChartCard(
    title: String,
    readout: String,
    values: List<Float>,
    ticks: (Int) -> String?,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    description: String,
) {
    val ink = Ink.ink
    val faint = Ink.track
    val current by rememberUpdatedState(selected)
    val pick by rememberUpdatedState(onSelect)
    val line = Ink.line
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = Type.heading, color = Ink.ink)
            Text(readout, style = Type.small, color = Ink.muted)
            val n = values.size
            val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .semantics { contentDescription = description }
                    .pointerInput(n) {
                        detectTapGestures { p ->
                            val i = (p.x / (size.width / n.toFloat())).toInt().coerceIn(0, n - 1)
                            pick(if (i == current) null else i)
                        }
                    },
            ) {
                drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                val slot = size.width / n
                val gap = 2.dp.toPx()
                val w = (slot - gap).coerceAtLeast(1f)
                for (i in 0 until n) {
                    val h = values[i] / max * (size.height - 4.dp.toPx())
                    if (h <= 0f) continue
                    val color = if (selected == null || selected == i) ink else faint
                    roundedTopBar(color, Offset(i * slot + gap / 2, size.height - h), Size(w, h), (4.dp.toPx()).coerceAtMost(w / 2))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                for (i in values.indices) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val t = ticks(i)
                        if (t != null) Text(t, style = Type.small.copy(fontSize = 11.sp), color = Ink.muted, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

/** A bar rounded only at its data end (the top), square on the baseline. */
private fun DrawScope.roundedTopBar(color: androidx.compose.ui.graphics.Color, topLeft: Offset, size: Size, r: Float) {
    val radius = r.coerceAtMost(size.height)
    val path = Path().apply {
        moveTo(topLeft.x, topLeft.y + size.height)
        lineTo(topLeft.x, topLeft.y + radius)
        quadraticBezierTo(topLeft.x, topLeft.y, topLeft.x + radius, topLeft.y)
        lineTo(topLeft.x + size.width - radius, topLeft.y)
        quadraticBezierTo(topLeft.x + size.width, topLeft.y, topLeft.x + size.width, topLeft.y + radius)
        lineTo(topLeft.x + size.width, topLeft.y + size.height)
        close()
    }
    drawPath(path, color)
}

/** A name, a value, and a thin bar showing its share. */
@Composable
private fun ShareRow(name: String, value: String, share: Float, sub: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(name, style = Type.body, color = Ink.ink, modifier = Modifier.weight(1f))
            Text(value, style = Type.body.copy(fontSize = 15.sp), color = Ink.ink, textAlign = TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).background(Ink.tile, RoundedCornerShape(3.dp))) {
            Box(
                Modifier
                    .fillMaxWidth(share.coerceIn(0.01f, 1f))
                    .fillMaxHeight()
                    .background(Ink.ink, RoundedCornerShape(3.dp)),
            )
        }
        if (sub != null) Text(sub, style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(2.dp))
    }
}
