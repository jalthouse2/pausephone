package com.jalthouse.pausephone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.jalthouse.pausephone.data.Kind
import com.jalthouse.pausephone.data.Member
import com.jalthouse.pausephone.data.Rule
import com.jalthouse.pausephone.data.Progress
import com.jalthouse.pausephone.data.Store
import com.jalthouse.pausephone.data.Usage
import com.jalthouse.pausephone.data.durationText
import com.jalthouse.pausephone.ui.Breath
import com.jalthouse.pausephone.ui.DotCloud
import com.jalthouse.pausephone.ui.Enso
import com.jalthouse.pausephone.ui.Ink
import com.jalthouse.pausephone.ui.LetterTile
import com.jalthouse.pausephone.ui.PrimaryButton
import com.jalthouse.pausephone.ui.QuietButton
import com.jalthouse.pausephone.ui.StepSlider
import com.jalthouse.pausephone.ui.ToggleRow
import com.jalthouse.pausephone.ui.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGES = 10

private val PICKUPS = listOf(10, 20, 30, 40, 50, 60, 80, 100, 120, 150)

/**
 * The introduction, ten quiet pages: three questions, what your answers add up to,
 * why [pause] exists, your first apps, one breath, the one permission, and that it's free.
 */
@Composable
fun Onboarding(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val next: () -> Unit = { if (page < PAGES - 1) page++ else onDone() }
    BackHandler(enabled = page > 0) { page-- }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        Progress(page)
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "page",
        ) { p ->
            Column(Modifier.fillMaxSize()) {
                when (p) {
                    0 -> Hello(next)
                    1 -> Question(
                        "When you pick up your phone, are you there?",
                        "Present: aware of what you're doing, and choosing it.",
                        listOf("always" to "Almost always", "sometimes" to "Sometimes", "rarely" to "Rarely, if I'm honest"),
                        Store.presence,
                    ) { Store.presence = it; next() }
                    2 -> Question(
                        "Do you usually know why you opened it?",
                        null,
                        listOf("yes" to "Yes, mostly", "sometimes" to "Sometimes", "no" to "I often end up somewhere I didn't mean to be"),
                        null,
                    ) { next() }
                    3 -> Pickups(next)
                    4 -> YourDay(next)
                    5 -> WhyPause(next)
                    6 -> FirstApps(next)
                    7 -> OneBreath(next)
                    8 -> Permission(next)
                    else -> Free(onDone)
                }
            }
        }
    }
}

/** Ten small dots; the ones you've passed are filled. */
@Composable
private fun Progress(page: Int) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(PAGES) { i ->
            Box(
                Modifier
                    .size(if (i == page) 8.dp else 6.dp)
                    .background(if (i <= page) Ink.ink else Ink.track, RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
private fun ColumnScope.Heading(text: String, sub: String? = null) {
    Text(text, style = Type.title.copy(fontSize = 30.sp, lineHeight = 37.sp), color = Ink.ink, modifier = Modifier.padding(top = 36.dp))
    if (sub != null) Text(sub, style = Type.body, color = Ink.muted, modifier = Modifier.padding(top = 12.dp))
}

// 1 ---------------------------------------------------------------

@Composable
private fun ColumnScope.Hello(next: () -> Unit) {
    Spacer(Modifier.weight(1f))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Enso(176.dp) }
    Text(
        "[pause]",
        style = Type.wordmark.copy(fontSize = 34.sp, fontWeight = FontWeight.Light, letterSpacing = 1.4.sp),
        color = Ink.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
    )
    Text(
        "take a pause. be present.",
        style = Type.body,
        color = Ink.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
    Spacer(Modifier.weight(1f))
    PrimaryButton("Begin", next, Modifier.fillMaxWidth())
}

// 2, 3 --------------------------------------------------------------

@Composable
private fun ColumnScope.Question(
    title: String,
    sub: String?,
    options: List<Pair<String, String>>,
    initial: String?,
    onPick: (String) -> Unit,
) {
    var picked by remember { mutableStateOf(initial) }
    val scope = rememberCoroutineScope()
    Heading(title, sub)
    Spacer(Modifier.height(32.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (key, label) ->
            val on = key == picked
            Text(
                label,
                style = Type.body,
                color = if (on) Ink.paper else Ink.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (on) Ink.ink else Ink.paper, RoundedCornerShape(18.dp))
                    .border(1.dp, if (on) Ink.ink else Ink.line, RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) {
                        picked = key
                        scope.launch { delay(280); onPick(key) }
                    }
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            )
        }
    }
    Spacer(Modifier.weight(1f))
}

// 4 -----------------------------------------------------------------

/** Your real numbers from the phone, if you allow it; otherwise a guess. */
@Composable
private fun ColumnScope.Pickups(next: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Usage.granted(context)) }
    var guessing by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<Usage.Summary?>(null) }
    LifecycleResumeEffect(Unit) {
        granted = Usage.granted(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(granted) {
        if (granted) {
            withContext(Dispatchers.IO) { Progress.captureBaseline(context) }
            val s = withContext(Dispatchers.IO) { Usage.lastDays(context, 7) }
            summary = s
            if (s != null && s.pickupsPerDay > 0) {
                Store.pickupsGuess = s.pickupsPerDay
                Store.pickupsReal = true
            }
            if (s != null) Store.usageMsPerDay = s.msPerDay
        }
    }
    val s = summary
    when {
        granted && s != null -> {
            Heading("Here's your last week.", "From your phone's own count. It stays on your phone.")
            Column(Modifier.weight(1f).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BigNumber("${s.pickupsPerDay}", "pickups a day")
                BigNumber(durationText(s.msPerDay), "on your phone each day")
                if (s.perApp.isNotEmpty()) {
                    Text("Where the time went", style = Type.heading, color = Ink.ink, modifier = Modifier.padding(top = 8.dp))
                    s.perApp.take(3).forEach { (pkg, ms) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Usage.label(context, pkg), style = Type.body, color = Ink.ink, modifier = Modifier.weight(1f))
                            Text(durationText(ms / s.days) + " a day", style = Type.body.copy(fontSize = 15.sp), color = Ink.muted)
                        }
                    }
                }
            }
            PrimaryButton("Continue", next, Modifier.fillMaxWidth())
        }
        granted -> {
            Heading("Looking at your week…")
            Spacer(Modifier.weight(1f))
        }
        guessing -> {
            Heading("How many times a day do you think you pick it up?", "A guess is fine. Most people guess low.")
            Spacer(Modifier.height(36.dp))
            StepSlider(
                "Pickups a day", "Each unlock, each glance that turns into a scroll",
                PICKUPS, Store.pickupsGuess.let { g -> PICKUPS.minByOrNull { kotlin.math.abs(it - g) } ?: 60 },
                { if (it == PICKUPS.last()) "$it or more" else "about $it" },
            ) { Store.pickupsGuess = it; Store.pickupsReal = false }
            Spacer(Modifier.weight(1f))
            PrimaryButton("Continue", next, Modifier.fillMaxWidth())
        }
        else -> {
            Heading(
                "Let's look at your real day.",
                "Your phone already counts how often you pick it up and how long you spend in each app. " +
                    "With your OK, [pause] can show you those numbers. They never leave your phone.",
            )
            Spacer(Modifier.weight(1f))
            Text(
                "In the list that opens, find [pause] and turn on Usage access, then come back.",
                style = Type.small,
                color = Ink.muted,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            PrimaryButton("Show my numbers", { Usage.openSettings(context) }, Modifier.fillMaxWidth())
            QuietButton("I'd rather guess", { guessing = true }, Modifier.fillMaxWidth().padding(top = 10.dp))
        }
    }
}

@Composable
private fun BigNumber(value: String, label: String) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, Ink.line, RoundedCornerShape(18.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = Type.stat.copy(fontSize = 34.sp), color = Ink.ink)
        Text(label, style = Type.small.copy(fontSize = 14.sp), color = Ink.muted)
    }
}

// 5 -----------------------------------------------------------------

/** One dot for every pickup: your own day, drawn. */
@Composable
private fun ColumnScope.YourDay(next: () -> Unit) {
    val n = Store.pickupsGuess
    val week = n * 7
    val year = n * 365
    val real = Store.pickupsReal
    Heading(
        "This is your day.",
        if (real) "Each dot is one time you pick up your phone, on an average day last week."
        else "Each dot is one time you pick up your phone.",
    )
    val dotColor = Ink.ink
    Canvas(Modifier.fillMaxWidth().padding(top = 28.dp).aspectRatio(1.6f)) {
        val cols = 15
        val rows = (n + cols - 1) / cols
        val gapX = size.width / cols
        val gapY = (size.height / maxOf(rows, 1)).coerceAtMost(gapX)
        val r = (minOf(gapX, gapY) * 0.22f).coerceAtMost(6.dp.toPx())
        for (i in 0 until n) {
            val cx = gapX * (i % cols) + gapX / 2
            val cy = gapY * (i / cols) + gapY / 2
            drawCircle(dotColor, r, Offset(cx, cy))
        }
    }
    val hours = Store.usageMsPerDay / 3_600_000.0
    val daysAYear = (Store.usageMsPerDay * 365 / 86_400_000L).toInt()
    Text(
        "That's about $week times a week, and more than ${"%,d".format(year)} times a year. " +
            (if (hours >= 1) "At this pace, your phone takes about $daysAYear full days of your year. " else "") +
            "Each pickup is a moment you could choose.",
        style = Type.body,
        color = Ink.ink,
        modifier = Modifier.padding(top = 20.dp),
    )
    Spacer(Modifier.weight(1f))
    PrimaryButton("Continue", next, Modifier.fillMaxWidth())
}

// 6 -----------------------------------------------------------------

@Composable
private fun ColumnScope.WhyPause(next: () -> Unit) {
    Heading("Not a wall. A breath.", "[pause] is here so that you choose your phone, instead of it choosing you.")
    Spacer(Modifier.height(24.dp))
    Column(Modifier.verticalScroll(rememberScrollState()).weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Point("A pause before the apps you choose", "A few seconds of breathing, then you decide: go in, or never mind.")
        Point("Limits you set yourself", "How often, how long, and when. Gentle, never locked away from you.")
        Point("No streaks, no scores", "Nothing to win or lose. Just a calmer way of being on your phone.")
    }
    PrimaryButton("Continue", next, Modifier.fillMaxWidth().padding(top = 12.dp))
}

@Composable
private fun Point(title: String, line: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Ink.line, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(width = 28.dp, height = 2.dp).background(Ink.ink))
        Text(title, style = Type.heading, color = Ink.ink, modifier = Modifier.padding(top = 6.dp))
        Text(line, style = Type.small.copy(fontSize = 14.sp, lineHeight = 20.sp), color = Ink.muted)
    }
}

// 7 -----------------------------------------------------------------

@Composable
private fun ColumnScope.FirstApps(next: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var showAll by remember { mutableStateOf(false) }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(Store.rules().map { it.id }) } }
    var usage by remember { mutableStateOf<Usage.Summary?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(context) }
        usage = withContext(Dispatchers.IO) { Usage.lastDays(context, 7) }
    }

    Heading(
        "Which apps pull you in?",
        if (usage?.perApp?.isNotEmpty() == true) "Your most-used apps are first. Choose a few to start; you can change this any time."
        else "Choose a few to start. You can change this any time.",
    )
    val list = apps
    Column(Modifier.weight(1f).padding(top = 16.dp).verticalScroll(rememberScrollState())) {
        if (list == null) {
            Text("Finding your apps…", style = Type.small, color = Ink.muted)
        } else {
            val used = usage?.perApp?.map { it.first }.orEmpty()
            val usual = if (used.isNotEmpty()) {
                used.mapNotNull { pkg -> list.find { it.pkg == pkg } }.take(8)
            } else {
                list.filter { it.pkg in USUAL_APPS }
            }
            val shown = if (showAll || usual.isEmpty()) list else usual
            shown.forEach { app ->
                val on = app.pkg in chosen
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Checkbox) { if (on) chosen.remove(app.pkg) else chosen.add(app.pkg) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (app.icon != null) Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    else LetterTile(app.label)
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = Type.body, color = Ink.ink)
                        val ms = usage?.perApp?.find { it.first == app.pkg }?.second
                        val days = usage?.days ?: 1
                        if (ms != null) Text(durationText(ms / days) + " a day", style = Type.small, color = Ink.muted)
                    }
                    Box(
                        Modifier
                            .size(24.dp)
                            .background(if (on) Ink.ink else Ink.paper, RoundedCornerShape(12.dp))
                            .border(1.5.dp, if (on) Ink.ink else Ink.track, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Box(Modifier.size(8.dp).background(Ink.paper, RoundedCornerShape(4.dp)))
                    }
                }
            }
            if (!showAll && usual.isNotEmpty()) {
                TextButton(onClick = { showAll = true }) { Text("Show all apps", style = Type.body, color = Ink.ink) }
            }
        }
    }
    PrimaryButton(
        if (chosen.isEmpty()) "Skip for now" else "Continue with ${chosen.size}",
        {
            val all = apps.orEmpty().associateBy { it.pkg }
            if (chosen.isNotEmpty()) {
                val group = Store.groups().firstOrNull() ?: Store.newGroup("Apps that pull me in")
                val members = group.members + chosen.filter { id -> group.members.none { it.id == id } }
                    .map { pkg -> Member(pkg, all[pkg]?.label ?: pkg, Kind.APP) }
                Store.setMembers(group.id, members)
            }
            next()
        },
        Modifier.fillMaxWidth().padding(top = 12.dp),
    )
}

// 8 -----------------------------------------------------------------

@Composable
private fun ColumnScope.OneBreath(next: () -> Unit) {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { clock.floatValue = (it - start) / 1_000_000_000f }
    }
    val rhythm = remember { Breath(4f) }
    val word by remember { derivedStateOf { rhythm.word(clock.floatValue) } }
    Heading("Let's take one together.", "Watch the dots. Breathe in as they spread, out as they gather.")
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        DotCloud(elapsed = { clock.floatValue }, breathSec = 4, color = Ink.ink, modifier = Modifier.size(280.dp))
    }
    Text(
        word,
        style = Type.body.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 2.sp),
        color = Ink.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
    )
    PrimaryButton("Continue", next, Modifier.fillMaxWidth())
}

// 9 -----------------------------------------------------------------

@Composable
private fun ColumnScope.Permission(next: () -> Unit) {
    val context = LocalContext.current
    val enabled = rememberServiceEnabled()
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Store.letterOn = granted
        WeeklyLetter.schedule(context)
    }
    Heading(
        "One permission, and why.",
        "To pause before an app, [pause] needs to notice when that app opens. Android calls this Accessibility. " +
            "[pause] only looks at which app is in front and the address bar of your browser. Nothing leaves your phone.",
    )
    Spacer(Modifier.height(24.dp))
    if (enabled) {
        Text("It's on. Thank you.", style = Type.heading, color = Ink.ink)
    } else {
        QuietButton("Open Accessibility settings", { openAccessibility(context) }, Modifier.fillMaxWidth())
        Text(
            "Find [pause] in the list and switch it on. If it's greyed out, open Settings › Apps › [pause], " +
                "tap ⋮ and choose Allow restricted settings.",
            style = Type.small,
            color = Ink.muted,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
    Spacer(Modifier.height(20.dp))
    ToggleRow("A letter on Sundays", "A few calm sentences about your week", Store.letterOn) { on ->
        if (on && Build.VERSION.SDK_INT >= 33) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Store.letterOn = on
            WeeklyLetter.schedule(context)
        }
    }
    Spacer(Modifier.weight(1f))
    PrimaryButton(if (enabled) "Continue" else "I'll do it later", next, Modifier.fillMaxWidth())
}

// 10 ----------------------------------------------------------------

@Composable
private fun ColumnScope.Free(onDone: () -> Unit) {
    val context = LocalContext.current
    Spacer(Modifier.weight(1f))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Enso(96.dp) }
    Text(
        "[pause] is free.",
        style = Type.title.copy(fontSize = 30.sp),
        color = Ink.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
    )
    Text(
        "No ads, no account, nothing to buy. And you're also free to buy me a coffee.",
        style = Type.body,
        color = Ink.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
    Spacer(Modifier.weight(1f))
    PrimaryButton("Start", onDone, Modifier.fillMaxWidth())
    QuietButton(
        "Buy me a coffee",
        { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TIP_URL))) },
        Modifier.fillMaxWidth().padding(top = 10.dp),
    )
}
