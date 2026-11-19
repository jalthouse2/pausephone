package com.jalthouse.pausephone

import android.Manifest
import android.app.NotificationManager
import android.content.Context
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

/** The answers to "how much time do you think you spend?": minutes stored, label, and the range it covers. */
private class Guess(val min: Int, val label: String, val said: String, val low: Int, val high: Int)

private val GUESSES = listOf(
    Guess(30, "Less than an hour", "less than an hour", 0, 60),
    Guess(120, "1 to 3 hours", "1 to 3 hours", 60, 180),
    Guess(240, "3 to 5 hours", "3 to 5 hours", 180, 300),
    Guess(360, "5 to 7 hours", "5 to 7 hours", 300, 420),
    Guess(480, "More than 7 hours", "more than 7 hours", 420, 24 * 60),
)

/**
 * The introduction, ten quiet pages: two questions, the permissions and why, your real week,
 * what it adds up to, why [pause] exists, your first apps, one breath, and that it's free.
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
                        "How much time do you think you spend on your phone each day?",
                        "A guess is fine. In a moment we can look at the real number.",
                        GUESSES.map { it.min.toString() to it.label },
                        Store.timeGuessMin.takeIf { it > 0 }?.toString(),
                    ) {
                        Store.timeGuessMin = it.toInt()
                        if (!Store.pickupsReal) Store.usageMsPerDay = it.toLong() * 60_000L
                        next()
                    }
                    3 -> Permissions(next)
                    4 -> Pickups(next)
                    5 -> YourDay(next)
                    6 -> WhyPause(next)
                    7 -> FirstApps(next)
                    8 -> OneBreath(next)
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
    var loaded by remember { mutableStateOf(false) }
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
            if (s != null && s.msPerDay > 0) Store.usageMsPerDay = s.msPerDay
            loaded = true
        }
    }
    val s = summary
    when {
        granted && s != null -> {
            Heading("Here's your last week.", guessLine(s.msPerDay) ?: "From your phone's own count. It stays on your phone.")
            Column(
                Modifier.weight(1f).padding(top = 24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
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
        granted && !loaded -> {
            Heading("Looking at your week…")
            Spacer(Modifier.weight(1f))
        }
        else -> {
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
    }
}

/** How your guess compares with what the phone counted. Never a scolding, just the two side by side. */
private fun guessLine(realMs: Long): String? {
    val g = GUESSES.find { it.min == Store.timeGuessMin } ?: return null
    val realMin = realMs / 60_000L
    return when {
        realMin > g.high -> "You guessed ${g.said}. Your phone says ${durationText(realMs)} a day. Most people guess low."
        realMin < g.low -> "You guessed ${g.said}. Your phone says ${durationText(realMs)} a day, a little less than you thought."
        else -> "You guessed ${g.said}, and your phone agrees: ${durationText(realMs)} a day."
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
    val ms = Store.usageMsPerDay
    val daysAYear = (ms * 365 / 86_400_000L).toInt()
    val quarter = ms / 4
    val daysBack = (quarter * 365 / 86_400_000L).toInt()
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Canvas(Modifier.fillMaxWidth().padding(top = 24.dp).aspectRatio(2.2f)) {
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
        Text(
            "That's about $week times a week, and more than ${"%,d".format(year)} times a year.",
            style = Type.body,
            color = Ink.ink,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
        )
        if (daysAYear >= 1) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BigNumber("$daysAYear days", "of your year on your phone, at ${durationText(ms)} a day" + if (Store.pickupsReal) "" else " (your guess)")
                if (daysBack >= 1) {
                    BigNumber("$daysBack days", "back each year, if you took back a quarter of it: ${durationText(quarter)} a day")
                }
            }
        }
        Text(
            "Not every minute on the phone is lost. The ones you choose are yours. [pause] helps with the rest.",
            style = Type.body,
            color = Ink.muted,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
    PrimaryButton("Continue", next, Modifier.fillMaxWidth().padding(top = 12.dp))
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

// 4 (permissions) ----------------------------------------------------

/** Each permission on its own switch, with the one reason it's needed. Every switch reflects the real state. */
@Composable
private fun ColumnScope.Permissions(next: () -> Unit) {
    val context = LocalContext.current
    val service = rememberServiceEnabled()
    var usage by remember { mutableStateOf(Usage.granted(context)) }
    var reminders by remember { mutableStateOf(remindersOn(context)) }
    LifecycleResumeEffect(Unit) {
        usage = Usage.granted(context)
        reminders = remindersOn(context)
        onPauseOrDispose { }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        setReminders(context, granted)
        reminders = remindersOn(context)
    }
    Heading(
        "To help, [pause] needs your OK.",
        "Each switch has one reason. Everything stays on your phone, and you can change any of them later.",
    )
    Column(
        Modifier.weight(1f).padding(top = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ToggleRow(
            "Screen time",
            "So [pause] can show you your real day: pickups, time, and which apps.",
            usage,
        ) { Usage.openSettings(context) }
        ToggleRow(
            "Pause before apps",
            "So [pause] notices when an app you chose opens. Android calls this Accessibility.",
            service,
        ) { openAccessibility(context) }
        ToggleRow(
            "Gentle reminders",
            "A morning intention and a Sunday letter. Nothing else.",
            reminders,
        ) { on ->
            val allowed = context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
            if (on && !allowed && Build.VERSION.SDK_INT >= 33) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                setReminders(context, on)
                reminders = remindersOn(context)
            }
        }
        if (!service) {
            Text(
                "In the list that opens, find [pause] and switch it on. If it's greyed out, open Settings › Apps › [pause], " +
                    "tap ⋮ and choose Allow restricted settings.",
                style = Type.small,
                color = Ink.muted,
            )
        }
    }
    PrimaryButton(if (usage && service) "Continue" else "Continue for now", next, Modifier.fillMaxWidth().padding(top = 12.dp))
}

private fun remindersOn(context: Context): Boolean =
    context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true &&
        (Store.letterOn || Store.intentionReminder)

private fun setReminders(context: Context, on: Boolean) {
    Store.letterOn = on
    Store.intentionReminder = on
    WeeklyLetter.schedule(context)
    DailyIntention.schedule(context)
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
