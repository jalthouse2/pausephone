package com.jalthouse.pausephone

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.jalthouse.pausephone.data.Kind
import com.jalthouse.pausephone.data.Rule
import com.jalthouse.pausephone.data.Store
import com.jalthouse.pausephone.data.Progress
import com.jalthouse.pausephone.data.TimeWindow
import com.jalthouse.pausephone.data.Usage
import com.jalthouse.pausephone.data.durationText
import com.jalthouse.pausephone.data.weekLetter
import com.jalthouse.pausephone.service.PauseService
import com.jalthouse.pausephone.ui.BackHeader
import com.jalthouse.pausephone.ui.Enso
import com.jalthouse.pausephone.ui.Ink
import com.jalthouse.pausephone.ui.LetterTile
import com.jalthouse.pausephone.ui.ListRow
import com.jalthouse.pausephone.ui.Panel
import com.jalthouse.pausephone.ui.PauseTheme
import com.jalthouse.pausephone.ui.PrimaryButton
import com.jalthouse.pausephone.ui.QuietButton
import com.jalthouse.pausephone.ui.StepSlider
import com.jalthouse.pausephone.ui.ToggleRow
import com.jalthouse.pausephone.ui.Type
import com.jalthouse.pausephone.ui.minutesText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class Tab(val label: String, val icon: ImageVector?) {
    HOME("Home", null),
    APPS("Apps", Icons.AutoMirrored.Outlined.List),
    TODAY("Today", Icons.Outlined.DateRange),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

/** Screens that open on top of a tab. */
internal sealed interface Screen {
    data object Unlock : Screen
    data class GroupEdit(val id: String) : Screen
    data class GroupPart(val id: String, val part: String) : Screen
    data class Choose(val groupId: String, val sites: Boolean) : Screen
    data class SettingsPart(val part: String) : Screen
    data object Breakdown : Screen
    data object Sit : Screen
    data object Intention : Screen
}

class MainActivity : ComponentActivity() {
    companion object {
        /** Set when the morning notification is tapped: open "Today's intention". */
        var openIntention by mutableStateOf(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(DailyIntention.EXTRA_OPEN, false)) openIntention = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        WeeklyLetter.schedule(this)
        DailyIntention.schedule(this)
        if (intent?.getBooleanExtra(DailyIntention.EXTRA_OPEN, false) == true) openIntention = true
        setContent {
            watchStore()
            Ink.dark = Store.theme == "dark"
            val view = LocalView.current
            SideEffect {
                window.statusBarColor = Ink.paper.toArgb()
                window.navigationBarColor = Ink.paper.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !Ink.dark
                    isAppearanceLightNavigationBars = !Ink.dark
                }
            }
            PauseTheme { App() }
        }
    }
}

@Composable
private fun App() {
    watchStore()
    var tab by remember { mutableStateOf(Tab.HOME) }
    val stack = remember { mutableStateListOf<Screen>() }
    val go: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    if (MainActivity.openIntention && Store.welcomeDone) {
        LaunchedEffect(Unit) {
            MainActivity.openIntention = false
            if (stack.lastOrNull() != Screen.Intention) stack.add(Screen.Intention)
        }
    }

    Column(Modifier.fillMaxSize().background(Ink.paper).systemBarsPadding()) {
        if (!Store.welcomeDone) {
            Onboarding { Store.welcomeDone = true }
        } else {
            Main(tab, { tab = it }, stack, go, back)
        }
    }
}

@Composable
private fun ColumnScope.Main(
    tab: Tab,
    setTab: (Tab) -> Unit,
    stack: MutableList<Screen>,
    go: (Screen) -> Unit,
    back: () -> Unit,
) {
        BackHandler(enabled = stack.isNotEmpty() || tab != Tab.HOME) {
            if (stack.isNotEmpty()) back() else setTab(Tab.HOME)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val top = stack.lastOrNull()
            if (top != null) {
                when (top) {
                    Screen.Unlock -> UnlockScreen(back)
                    is Screen.Choose -> Guarded("Changing your apps and sites?", back) {
                        ChooseScreen(top.groupId, top.sites, onDone = back, back = back)
                    }
                    is Screen.GroupEdit -> GroupScreen(top.id, go, back)
                    is Screen.GroupPart -> Guarded("Changing a rule?", back) { GroupPartScreen(top.id, top.part, back) }
                    is Screen.SettingsPart -> SettingsPartScreen(top.part, go, back)
                    Screen.Breakdown -> BreakdownScreen(back)
                    Screen.Sit -> SitScreen(back)
                    Screen.Intention -> IntentionScreen(back)
                }
            } else {
                when (tab) {
                    Tab.HOME -> HomeScreen(go)
                    Tab.APPS -> GroupsScreen(go)
                    Tab.TODAY -> TodayScreen(go)
                    Tab.SETTINGS -> SettingsScreen(go)
                }
            }
        }
        if (stack.isEmpty()) TabBar(tab, setTab)
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column {
        HorizontalDivider(color = Ink.line)
        NavigationBar(containerColor = Ink.paper, tonalElevation = 0.dp) {
            Tab.entries.forEach { t ->
                NavigationBarItem(
                    selected = t == selected,
                    onClick = { onSelect(t) },
                    icon = {
                        val icon = t.icon
                        if (icon == null) Enso(24.dp, if (t == selected) Ink.ink else Ink.muted)
                        else Icon(icon, contentDescription = null)
                    },
                    label = { Text(t.label, style = Type.small.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Ink.ink,
                        selectedTextColor = Ink.ink,
                        unselectedIconColor = Ink.muted,
                        unselectedTextColor = Ink.muted,
                        indicatorColor = Ink.tile,
                    ),
                )
            }
        }
    }
}

/** Reading the store's version makes the calling screen redraw whenever the store changes. */
@Composable
internal fun watchStore(): Int = Store.version

@Composable
internal fun TabTitle(text: String) {
    Text(text, style = Type.title, color = Ink.ink, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp))
}

internal fun serviceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val ours = ComponentName(context, PauseService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
}

@Composable
internal fun rememberServiceEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(serviceEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = serviceEnabled(context)
        onPauseOrDispose { }
    }
    return enabled
}

internal fun openAccessibility(context: Context) =
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

internal fun clock(millis: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))

// ---------------------------------------------------------------- home

/** Almost nothing: the ensō. Tap it to breathe. */
@Composable
private fun HomeScreen(go: (Screen) -> Unit) {
    watchStore()
    val context = LocalContext.current
    val enabled = rememberServiceEnabled()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Breathe",
            ) { go(Screen.Sit) },
        ) { Enso(168.dp) }
        Text(
            "tap to sit and breathe",
            style = Type.small,
            color = Ink.muted,
            modifier = Modifier.padding(top = 20.dp),
        )
        val todays = Store.todayIntention()
        TextButton(onClick = { go(Screen.Intention) }, modifier = Modifier.padding(top = 16.dp)) {
            Text(
                if (todays != null) "today · $todays" else "set an intention for today",
                style = if (todays != null) Type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Light) else Type.small,
                color = if (todays != null) Ink.ink else Ink.muted,
            )
        }
        Spacer(Modifier.weight(1f))
        when {
            !enabled -> TextButton(onClick = { openAccessibility(context) }) {
                Text("Turn on [pause] in Accessibility to begin", style = Type.small, color = Ink.ink)
            }
            Store.onBreak() -> Text(
                "On a break until ${clock(Store.breakUntil)}",
                style = Type.small,
                color = Ink.muted,
            )
        }
    }
}

// ---------------------------------------------------------------- today

@Composable
private fun TodayScreen(go: (Screen) -> Unit) {
    watchStore()
    val stats = Store.today()
    val rules = Store.rules()
    val day = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                when (stats.pauses) {
                    0 -> "No pauses yet today."
                    1 -> "You paused once today."
                    else -> "You paused ${stats.pauses} times today."
                },
                style = Type.title.copy(fontSize = 30.sp, lineHeight = 36.sp),
                color = Ink.ink,
            )
            Text(day, style = Type.body.copy(fontSize = 15.sp), color = Ink.muted)
        }

        PhoneToday { go(Screen.Breakdown) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("${stats.wentIn}", "went in", Modifier.weight(1f))
            StatBox("${stats.letGo}", "let it go", Modifier.weight(1f))
            StatBox("${stats.breathers}", "breathers", Modifier.weight(1f))
        }

        val went = stats.intents.entries.sortedByDescending { it.value }
        if (went.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("What you went in for", style = Type.heading, color = Ink.ink)
                Text(
                    went.joinToString(" · ") { "${it.key} ${it.value}" },
                    style = Type.body.copy(fontSize = 15.sp),
                    color = Ink.muted,
                )
            }
        }

        val context = LocalContext.current
        var phoneLine by remember { mutableStateOf<String?>(null) }
        var firstWeek by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) { Progress.captureBaseline(context) }
            phoneLine = withContext(Dispatchers.IO) { Usage.weekSentence(context) }
            firstWeek = withContext(Dispatchers.IO) { Progress.story(context, rules) }
        }
        firstWeek?.let { story ->
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(width = 28.dp, height = 2.dp).background(Ink.ink))
                    Text("Since you started", style = Type.heading, color = Ink.ink, modifier = Modifier.padding(top = 4.dp))
                    Text(story, style = Type.body, color = Ink.ink)
                }
            }
        }
        val letter = weekLetter(rules, phoneLine)
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (LocalDate.now().dayOfWeek == java.time.DayOfWeek.SUNDAY) "Your week" else "This week so far",
                    style = Type.heading,
                    color = Ink.ink,
                )
                Text(letter, style = Type.body, color = Ink.ink)
            }
        }

        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Opens left today", style = Type.heading, color = Ink.ink, modifier = Modifier.padding(bottom = 4.dp))
            if (rules.isEmpty()) {
                Text("Add apps and sites in the Apps tab.", style = Type.small, color = Ink.muted)
            }
            rules.forEachIndexed { i, r ->
                val trailing = when {
                    r.blocked -> "blocked"
                    r.opensPerDay == 0 -> "no limit"
                    else -> "${(r.opensPerDay - (stats.opens[r.id] ?: 0)).coerceAtLeast(0)} of ${r.opensPerDay}"
                }
                ListRow(r.label, null, { LetterTile(r.label) }, null, trailing, divider = i < rules.lastIndex)
            }
        }
    }
}

/** Today's screen time and pickups from the phone's own count, shown quietly. */
@Composable
private fun PhoneToday(onOpen: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Usage.granted(context)) }
    var today by remember { mutableStateOf<Usage.Summary?>(null) }
    var tick by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        granted = Usage.granted(context)
        tick++
        onPauseOrDispose { }
    }
    LaunchedEffect(granted, tick) {
        today = if (granted) withContext(Dispatchers.IO) { Usage.today(context) } else null
    }
    val t = today
    if (!granted) {
        Panel(Modifier.clickable(role = Role.Button) { Usage.openSettings(context) }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("See your screen time here", style = Type.heading, color = Ink.ink)
                Text(
                    "Turn on Usage access for [pause] to see today's time and pickups, from your phone's own count.",
                    style = Type.small,
                    color = Ink.muted,
                )
            }
        }
    } else if (t != null) {
        Panel(Modifier.clickable(role = Role.Button, onClick = onOpen)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("On your phone today", style = Type.heading, color = Ink.ink)
                Text(
                    "${durationText(t.totalMs)} · ${t.pickups} ${if (t.pickups == 1) "pickup" else "pickups"}",
                    style = Type.stat.copy(fontSize = 22.sp),
                    color = Ink.ink,
                )
                t.perApp.firstOrNull()?.let { (pkg, ms) ->
                    Text(
                        "Most of it in ${Usage.label(context, pkg)}, ${durationText(ms)}.",
                        style = Type.small,
                        color = Ink.muted,
                    )
                }
                Text("See where your time goes", style = Type.body.copy(fontSize = 15.sp), color = Ink.ink, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .border(1.dp, Ink.line, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = Type.stat, color = Ink.ink)
        Text(label, style = Type.small, color = Ink.muted)
    }
}

internal const val TIP_URL = "https://www.buymeacoffee.com/hX1y1MzNAx"

/**
 * One status panel: soft red while [pause] can't work (Accessibility off, or Android has
 * stopped it); plain and calm when all is set.
 */
@Composable
internal fun HealthCheck(enabled: Boolean) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1500)
            tick++
        }
    }
    val running = PauseService.running
    val ok = enabled && running
    val last = PauseService.lastPackage
    // Reading tick redraws this every 1.5 s.
    val ago = if (tick >= 0) (System.currentTimeMillis() - PauseService.lastSeenAt) / 1000 else 0L
    val title = when {
        !enabled -> "[pause] isn't on yet"
        !running -> "[pause] has stopped"
        else -> "All set"
    }
    val lines = buildList {
        when {
            !enabled -> {
                add("It needs Accessibility to notice when you open an app or site. Tap here, find [pause], and switch it on.")
                add("If the switch is greyed out: Settings › Apps › [pause] › ⋮ › Allow restricted settings.")
            }
            !running -> {
                add("Accessibility is on, but Android isn't running [pause] right now. Tap here and switch it off and on again.")
                add("If this keeps happening, set [pause] to Unrestricted in its battery settings.")
            }
            else -> add("[pause] is running.")
        }
        if (Store.onBreak()) add("You're on a break until ${clock(Store.breakUntil)}, so there are no pauses until then.")
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (ok) Ink.paper else Ink.alertBg, shape)
            .then(if (ok) Modifier.border(1.dp, Ink.line, shape) else Modifier)
            .then(if (!ok) Modifier.clickable(role = Role.Button) { openAccessibility(context) } else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = Type.heading, color = if (ok) Ink.ink else Ink.alertInk)
        lines.forEach { Text(it, style = Type.small, color = if (ok) Ink.muted else Ink.alertInk) }
        if (!ok) Text("Tap to turn it on", style = Type.body.copy(fontSize = 15.sp), color = Ink.alertInk, modifier = Modifier.padding(top = 6.dp))
    }
}

/** A short list you can add to and remove from. */
@Composable
internal fun EditableList(
    title: String,
    subtitle: String,
    items: List<String>,
    addLabel: String,
    placeholder: String,
    suggestions: List<String> = emptyList(),
    onChange: (List<String>) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    val more = suggestions.filter { it !in items }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = Type.heading, color = Ink.ink)
            Text(subtitle, style = Type.small, color = Ink.muted)
            items.forEachIndexed { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(item, style = Type.body, color = Ink.ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onChange(items.filterIndexed { j, _ -> j != i }) }) {
                        Text("Remove", style = Type.small, color = Ink.muted)
                    }
                }
            }
            if (items.isEmpty()) Text("Nothing here right now. Tap a suggestion below, or write your own.", style = Type.small, color = Ink.muted)
            QuietButton(addLabel, { adding = true }, Modifier.fillMaxWidth().padding(top = 4.dp).height(48.dp))
            if (more.isNotEmpty()) {
                Text("Suggestions", style = Type.small, color = Ink.muted, modifier = Modifier.padding(top = 10.dp))
                SuggestionChips(more) { onChange(items + it) }
            }
        }
    }
    if (adding) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = Ink.paper,
            title = { Text(addLabel, style = Type.heading, color = Ink.ink) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    placeholder = { Text(placeholder, style = Type.body, color = Ink.muted) },
                    textStyle = Type.body,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Ink.ink,
                        unfocusedBorderColor = Ink.track,
                        cursorColor = Ink.ink,
                        focusedTextColor = Ink.ink,
                        unfocusedTextColor = Ink.ink,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = text.trim()
                    if (t.isNotEmpty()) onChange(items + t)
                    adding = false
                }) { Text("Add", style = Type.button, color = Ink.ink) }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }) { Text("Cancel", style = Type.button, color = Ink.muted) }
            },
        )
    }
}

/** Tap a chip to add it. Removed items always come back here, so nothing is gone for good. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SuggestionChips(options: List<String>, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            Text(
                "+ $option",
                style = Type.small.copy(fontSize = 14.sp),
                color = Ink.ink,
                modifier = Modifier
                    .border(1.dp, Ink.track, RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { onPick(option) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/** A row of choices, one selected. */
@Composable
internal fun Choice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            val on = key == selected
            if (on) {
                Button(
                    onClick = { onSelect(key) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink.ink, contentColor = Ink.paper),
                ) { Text(label, style = Type.small.copy(fontWeight = FontWeight.Medium), maxLines = 1) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(key) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    border = BorderStroke(1.dp, Ink.track),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink.ink),
                ) { Text(label, style = Type.small.copy(fontWeight = FontWeight.Medium), maxLines = 1) }
            }
        }
    }
}

@Composable
internal fun AddSiteDialog(onDone: (String?) -> Unit) {
    var text by remember { mutableStateOf("") }
    val domain = PauseService.hostOf(text)
    AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = Ink.paper,
        title = { Text("Add a website", style = Type.heading, color = Ink.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("reddit.com", style = Type.body, color = Ink.muted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    textStyle = Type.body,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Ink.ink,
                        unfocusedBorderColor = Ink.track,
                        cursorColor = Ink.ink,
                    ),
                )
                if (text.isNotBlank() && domain == null) {
                    Text("Enter an address like reddit.com", style = Type.small, color = Ink.muted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (domain != null) onDone(domain) }) {
                Text("Add", style = Type.button, color = Ink.ink)
            }
        },
        dismissButton = {
            TextButton(onClick = { onDone(null) }) { Text("Cancel", style = Type.button, color = Ink.muted) }
        },
    )
}

internal data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

internal fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val px = (40 * context.resources.displayMetrics.density).toInt()
    return pm.queryIntentActivities(intent, 0)
        .asSequence()
        .map { it.activityInfo }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .map { info ->
            AppEntry(
                pkg = info.packageName,
                label = info.loadLabel(pm).toString(),
                icon = runCatching { info.loadIcon(pm).toBitmap(px, px).asImageBitmap() }.getOrNull(),
            )
        }
        .sortedBy { it.label.lowercase() }
        .toList()
}

// ---------------------------------------------------------------- steps for a group's settings

internal val PAUSES = (0..10).toList()
internal val BREATHS = (3..8).toList()
internal const val BLOCKED = -1
internal val OPENS = listOf(BLOCKED) + (1..20).toList() + 0                    // -1 = none (blocked), 0 = no limit
internal val SESSIONS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 150, 180, 210, 240, 0) // 0 = no limit
internal val BREATHERS = listOf(0, 5, 10, 15, 20, 30, 45, 60)                  // 0 = never

// ---------------------------------------------------------------- unlock

@Composable
private fun UnlockScreen(back: () -> Unit) {
    watchStore()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        BackHeader("Unlock pause", back)
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "A few seconds of breathing each time you open your phone, before anything else.",
                style = Type.body,
                color = Ink.muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            StepSlider(
                "Pause on unlock", "0 turns it off",
                PAUSES, Store.unlockPauseSec, { if (it == 0) "off" else "$it s" },
            ) { Store.unlockPauseSec = it }
            StepSlider(
                "Breath length", "One breath in, one breath out",
                BREATHS, Store.unlockBreathSec, { "$it s each way" },
            ) { Store.unlockBreathSec = it }
        }
    }
}

// ---------------------------------------------------------------- block at set times

@Composable
internal fun BlockTimes(label: String, times: List<TimeWindow>, onChange: (List<TimeWindow>) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Block at set times", style = Type.heading, color = Ink.ink)
            Text(
                if (times.isEmpty()) "Choose hours of the day when $label rests, every day."
                else "$label rests during these hours, every day.",
                style = Type.small,
                color = Ink.muted,
            )
            times.forEachIndexed { i, w ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("${w.start} to ${w.end}", style = Type.body, color = Ink.ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onChange(times.filterIndexed { j, _ -> j != i }) }) {
                        Text("Remove", style = Type.small, color = Ink.muted)
                    }
                }
            }
            QuietButton("Add a time", { adding = true }, Modifier.fillMaxWidth().padding(top = 4.dp).height(48.dp))
        }
    }
    if (adding) {
        AddTimeDialog(
            onDone = { w ->
                adding = false
                if (w != null) onChange(times + w)
            },
        )
    }
}

/** Pick a start, then an end. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTimeDialog(onDone: (TimeWindow?) -> Unit) {
    var start by remember { mutableStateOf<Int?>(null) }
    val fromState = rememberTimePickerState(initialHour = 22, initialMinute = 0, is24Hour = true)
    val untilState = rememberTimePickerState(initialHour = 7, initialMinute = 0, is24Hour = true)
    val state = if (start == null) fromState else untilState
    val picking = if (start == null) "From" else "Until"
    AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = Ink.paper,
        title = {
            Text(
                if (start == null) "From" else "From ${TimeWindow.hhmm(start!!)} until",
                style = Type.heading,
                color = Ink.ink,
            )
        },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Ink.tile,
                    clockDialSelectedContentColor = Ink.paper,
                    clockDialUnselectedContentColor = Ink.ink,
                    selectorColor = Ink.ink,
                    containerColor = Ink.paper,
                    periodSelectorBorderColor = Ink.track,
                    timeSelectorSelectedContainerColor = Ink.tile,
                    timeSelectorUnselectedContainerColor = Ink.paper,
                    timeSelectorSelectedContentColor = Ink.ink,
                    timeSelectorUnselectedContentColor = Ink.muted,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val m = state.hour * 60 + state.minute
                val s0 = start
                if (s0 == null) {
                    start = m
                } else if (m != s0) {
                    onDone(TimeWindow(s0, m))
                }
            }) { Text(if (picking == "From") "Next" else "Add", style = Type.button, color = Ink.ink) }
        },
        dismissButton = {
            TextButton(onClick = { onDone(null) }) { Text("Cancel", style = Type.button, color = Ink.muted) }
        },
    )
}
