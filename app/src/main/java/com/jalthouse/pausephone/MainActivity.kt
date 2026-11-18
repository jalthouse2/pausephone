package com.jalthouse.pausephone

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    APPS("Apps", Icons.AutoMirrored.Outlined.List),
    TODAY("Today", Icons.Outlined.DateRange),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

/** Screens that open on top of a tab. */
private sealed interface Screen {
    data object Unlock : Screen
    data object AddApp : Screen
    data class RuleEdit(val id: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContent {
            watchStore()
            val systemDark = isSystemInDarkTheme()
            Ink.dark = when (Store.theme) {
                "dark" -> true
                "system" -> systemDark
                else -> false
            }
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
    var welcomed by remember { mutableStateOf(Store.welcomeDone) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    val stack = remember { mutableStateListOf<Screen>() }
    val go: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    Column(Modifier.fillMaxSize().background(Ink.paper).systemBarsPadding()) {
        if (!welcomed) {
            Welcome { Store.welcomeDone = true; welcomed = true }
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
                    Screen.AddApp -> AddAppScreen(
                        onPicked = { id -> stack.removeAt(stack.lastIndex); go(Screen.RuleEdit(id)) },
                        back = back,
                    )
                    is Screen.RuleEdit -> RuleScreen(top.id, back)
                }
            } else {
                when (tab) {
                    Tab.HOME -> HomeScreen()
                    Tab.APPS -> AppsScreen(go)
                    Tab.TODAY -> TodayScreen()
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
                    icon = { Icon(t.icon, contentDescription = null) },
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
private fun watchStore(): Int = Store.version

@Composable
private fun TabTitle(text: String) {
    Text(text, style = Type.title, color = Ink.ink, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp))
}

private fun serviceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val ours = ComponentName(context, PauseService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
}

@Composable
private fun rememberServiceEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(serviceEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = serviceEnabled(context)
        onPauseOrDispose { }
    }
    return enabled
}

private fun openAccessibility(context: Context) =
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

private fun clock(millis: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))

// ---------------------------------------------------------------- welcome

@Composable
private fun Welcome(onBegin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 110.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Enso(184.dp)
        Text(
            "[pause]",
            style = Type.wordmark.copy(fontSize = 34.sp, fontWeight = FontWeight.Light, letterSpacing = 1.4.sp),
            color = Ink.ink,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text("take a pause. be present.", style = Type.body, color = Ink.muted, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.weight(1f))
        PrimaryButton("Begin", onBegin, Modifier.fillMaxWidth())
        Text(
            "Choose your apps, your pauses and your limits.",
            style = Type.small,
            color = Ink.muted,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

// ---------------------------------------------------------------- home

/** Almost nothing: the ensō. Tap it to breathe. */
@Composable
private fun HomeScreen() {
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
            ) { context.startActivity(PauseActivity.launchIntent(context, PauseMode.FREE, null)) },
        ) { Enso(168.dp) }
        Text(
            "tap to breathe",
            style = Type.small,
            color = Ink.muted,
            modifier = Modifier.padding(top = 20.dp),
        )
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
private fun TodayScreen() {
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

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("${stats.wentIn}", "went in", Modifier.weight(1f))
            StatBox("${stats.letGo}", "let it go", Modifier.weight(1f))
            StatBox("${stats.breathers}", "breathers", Modifier.weight(1f))
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

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .background(Ink.panel, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = Type.stat, color = Ink.ink)
        Text(label, style = Type.small, color = Ink.muted)
    }
}

// ---------------------------------------------------------------- apps and sites

private fun summary(r: Rule): String {
    if (r.blocked) return "blocked"
    val parts = mutableListOf(if (r.pauseSec == 0) "no pause" else "${r.pauseSec} s pause")
    if (r.opensPerDay > 0) parts += "${r.opensPerDay} opens"
    if (r.sessionMin > 0) parts += minutesText(r.sessionMin) + " sessions"
    return parts.joinToString(" · ")
}

@Composable
private fun AppsScreen(go: (Screen) -> Unit) {
    watchStore()
    val rules = Store.rules()
    val apps = rules.filter { it.kind == Kind.APP }
    val sites = rules.filter { it.kind == Kind.SITE }
    var addingSite by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TabTitle("Apps and sites")
        Column {
            Text("Apps", style = Type.heading, color = Ink.ink, modifier = Modifier.padding(bottom = 4.dp))
            if (apps.isEmpty()) Text("None yet.", style = Type.small, color = Ink.muted)
            apps.forEachIndexed { i, r ->
                ListRow(r.label, summary(r), { LetterTile(r.label) }, { go(Screen.RuleEdit(r.id)) }, divider = i < apps.lastIndex)
            }
        }
        Column {
            Text("Websites", style = Type.heading, color = Ink.ink, modifier = Modifier.padding(bottom = 4.dp))
            if (sites.isEmpty()) Text("None yet.", style = Type.small, color = Ink.muted)
            sites.forEachIndexed { i, r ->
                ListRow(r.label, summary(r), { LetterTile(r.label) }, { go(Screen.RuleEdit(r.id)) }, divider = i < sites.lastIndex)
            }
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton("Add an app", { go(Screen.AddApp) }, Modifier.fillMaxWidth())
        QuietButton("Add a website", { addingSite = true }, Modifier.fillMaxWidth())
        Text(
            "Websites work in Chrome, Firefox, Samsung Internet, Edge, Brave, Opera and DuckDuckGo.",
            style = Type.small,
            color = Ink.muted,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }

    if (addingSite) {
        AddSiteDialog(
            onDone = { domain ->
                addingSite = false
                if (domain != null) {
                    if (Store.rule(domain) == null) Store.saveRule(Rule(id = domain, label = domain, kind = Kind.SITE))
                    go(Screen.RuleEdit(domain))
                }
            },
        )
    }
}

// ---------------------------------------------------------------- settings

@Composable
private fun SettingsScreen(go: (Screen) -> Unit) {
    watchStore()
    val context = LocalContext.current
    val enabled = rememberServiceEnabled()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TabTitle("Settings")

        Panel(Modifier.clickable(role = Role.Button) { openAccessibility(context) }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Accessibility", style = Type.heading, color = Ink.ink)
                Text(
                    if (enabled) "On. [pause] can step in before your apps and sites."
                    else "Off. Tap to turn on [pause] so it can step in before your apps and sites.",
                    style = Type.small,
                    color = Ink.muted,
                )
            }
        }

        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Take a break", style = Type.heading, color = Ink.ink)
                if (Store.onBreak()) {
                    Text("On a break until ${clock(Store.breakUntil)}. No pauses until then.", style = Type.small, color = Ink.muted)
                    QuietButton("Resume now", { Store.breakUntil = 0L }, Modifier.fillMaxWidth())
                } else {
                    Text("Set [pause] aside for a while. Your rules come back on their own.", style = Type.small, color = Ink.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuietButton("1 hour", { Store.breakUntil = System.currentTimeMillis() + 3_600_000L }, Modifier.weight(1f))
                        QuietButton("Until tomorrow", {
                            val tomorrow = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
                            Store.breakUntil = tomorrow.toInstant().toEpochMilli()
                        }, Modifier.weight(1f))
                    }
                }
            }
        }

        Panel(Modifier.clickable(role = Role.Button) { go(Screen.Unlock) }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Unlock pause", style = Type.heading, color = Ink.ink)
                Text(
                    if (Store.unlockPauseSec == 0) "Off" else "${Store.unlockPauseSec} s of breathing each time you open your phone",
                    style = Type.small,
                    color = Ink.muted,
                )
            }
        }

        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Appearance", style = Type.heading, color = Ink.ink)
                Choice(
                    options = listOf("light" to "Light", "dark" to "Black", "system" to "Match phone"),
                    selected = Store.theme,
                    onSelect = { Store.theme = it },
                )
            }
        }
        ToggleRow(
            "Light breathing screen",
            "Dark dots on white instead of white dots on black",
            Store.breatheLight,
        ) { Store.breatheLight = it }

        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("About", style = Type.heading, color = Ink.ink)
                Text(
                    "Nothing leaves your phone. No account, no tracking, no ads.",
                    style = Type.small,
                    color = Ink.muted,
                )
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jalthouse2/pausephone/issues")),
                        )
                    },
                ) { Text("Report a bug or suggest an idea", style = Type.body, color = Ink.ink) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** A row of choices, one selected. */
@Composable
private fun Choice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
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
private fun AddSiteDialog(onDone: (String?) -> Unit) {
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

private data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

@Composable
private fun AddAppScreen(onPicked: (String) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(context) }
    }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        BackHeader("Add an app", back)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search your apps", style = Type.body, color = Ink.muted) },
            textStyle = Type.body,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink.ink,
                unfocusedBorderColor = Ink.track,
                cursorColor = Ink.ink,
            ),
        )
        val list = apps
        if (list == null) {
            Text("Finding your apps…", style = Type.small, color = Ink.muted, modifier = Modifier.padding(24.dp))
        } else {
            val shown = list.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            LazyColumn(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                items(shown, key = { it.pkg }) { app ->
                    ListRow(
                        app.label,
                        null,
                        {
                            if (app.icon != null) Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                            else LetterTile(app.label)
                        },
                        {
                            if (Store.rule(app.pkg) == null) {
                                Store.saveRule(Rule(id = app.pkg, label = app.label, kind = Kind.APP))
                            }
                            onPicked(app.pkg)
                        },
                    )
                }
            }
        }
    }
}

private fun loadApps(context: Context): List<AppEntry> {
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

// ---------------------------------------------------------------- one rule

private val PAUSES = (0..10).toList()
private val BREATHS = (3..8).toList()
private val OPENS = (1..20).toList() + 0                                      // 0 = no limit
private val SESSIONS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 150, 180, 210, 240, 0) // 0 = no limit
private val BREATHERS = listOf(0, 5, 10, 15, 20, 30, 45, 60)                  // 0 = never

@Composable
private fun RuleScreen(id: String, back: () -> Unit) {
    watchStore()
    val rule = Store.rule(id)
    if (rule == null) {
        LaunchedEffect(id) { back() }
        return
    }
    fun save(r: Rule) = Store.saveRule(r)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        BackHeader(rule.label, back)
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StepSlider(
                "Pause before opening", "Breathing before it opens",
                PAUSES, rule.pauseSec, { if (it == 0) "none" else "$it s" },
            ) { save(rule.copy(pauseSec = it)) }
            StepSlider(
                "Breath length", "One breath in, one breath out",
                BREATHS, rule.breathSec, { "$it s each way" },
            ) { save(rule.copy(breathSec = it)) }
            StepSlider(
                "Opens per day", "After that it stays closed until tomorrow",
                OPENS, rule.opensPerDay, { if (it == 0) "no limit" else if (it == 1) "1 open" else "$it opens" },
            ) { save(rule.copy(opensPerDay = it)) }
            StepSlider(
                "Session length", "Then the breathing cloud returns",
                SESSIONS, rule.sessionMin, { if (it == 0) "no limit" else minutesText(it) },
            ) { save(rule.copy(sessionMin = it)) }
            StepSlider(
                "A breather along the way", "After this much time in the app",
                BREATHERS, rule.breatherMin, { if (it == 0) "never" else minutesText(it) },
            ) { save(rule.copy(breatherMin = it)) }

            Spacer(Modifier.height(8.dp))
            ToggleRow(
                "Block at night",
                "${Store.nightStart} to ${Store.nightEnd}",
                rule.nightBlock,
            ) { save(rule.copy(nightBlock = it)) }
            ToggleRow(
                "Block completely",
                "It stays closed until you turn this off",
                rule.blocked,
            ) { save(rule.copy(blocked = it)) }
            TextButton(
                onClick = { Store.removeRule(id); back() },
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            ) { Text("Remove ${rule.label}", style = Type.button, color = Ink.muted) }
        }
    }
}

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
