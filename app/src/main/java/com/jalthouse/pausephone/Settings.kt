package com.jalthouse.pausephone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jalthouse.pausephone.data.Store
import com.jalthouse.pausephone.ui.BackHeader
import com.jalthouse.pausephone.ui.Ink
import com.jalthouse.pausephone.ui.Panel
import com.jalthouse.pausephone.ui.QuietButton
import com.jalthouse.pausephone.ui.StepSlider
import com.jalthouse.pausephone.ui.ToggleRow
import com.jalthouse.pausephone.ui.Type

private val PARTS = listOf(
    Triple("pauses", "Your pauses", "Unlock pause, pauses that grow, heads-up, when your day starts"),
    Triple("atpause", "At the pause", "Going in to…, and things to do instead"),
    Triple("breathing", "Breathing", "A tap with each breath, a bell at the rest"),
    Triple("appearance", "Appearance", "Light or black, and the breathing screen"),
    Triple("intention", "Morning intention", "A question each morning, and today's intention"),
    Triple("letter", "Sunday letter", "A few calm sentences about your week"),
    Triple("about", "About", "Privacy, a coffee, bugs and ideas, the introduction"),
)

/** Runs [action], which loosens your rules, after a breath when that setting is on. */
@Composable
private fun rememberGuard(onGate: (Pair<String, () -> Unit>) -> Unit): (String, () -> Unit) -> Unit =
    { reason, action -> if (Store.guardSec > 0) onGate(reason to action) else action() }

/** A short main page, with categories to tap into. */
@Composable
internal fun SettingsScreen(go: (Screen) -> Unit) {
    watchStore()
    var pending by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val p = pending
    if (p != null) {
        BreathGate(p.first, onPass = { p.second(); pending = null }, onCancel = { pending = null })
    } else {
        SettingsMain(go, rememberGuard { pending = it })
    }
}

@Composable
private fun SettingsMain(go: (Screen) -> Unit, guard: (String, () -> Unit) -> Unit) {
    val context = LocalContext.current
    val enabled = rememberServiceEnabled()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TabTitle("Settings")

        HealthCheck(enabled)

        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Take a break", style = Type.heading, color = Ink.ink)
                if (Store.onBreak()) {
                    Text("On a break until ${clock(Store.breakUntil)}. No pauses until then.", style = Type.small, color = Ink.muted)
                    QuietButton("Resume now", { Store.breakUntil = 0L }, Modifier.fillMaxWidth())
                } else {
                    Text("Set [pause] aside for a while. Your rules come back on their own.", style = Type.small, color = Ink.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuietButton("1 hour", {
                            guard("Taking a break?") { Store.breakUntil = System.currentTimeMillis() + 3_600_000L }
                        }, Modifier.weight(1f))
                        QuietButton("Until tomorrow", {
                            guard("Taking a break?") { Store.breakUntil = Store.nextDayStart() }
                        }, Modifier.weight(1f))
                    }
                }
            }
        }


        PARTS.forEach { (key, title, sub) ->
            Panel(Modifier.clickable(role = Role.Button) { go(Screen.SettingsPart(key)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = Type.heading, color = Ink.ink)
                        Text(sub, style = Type.small, color = Ink.muted)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Ink.muted)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** One category of settings on its own page. */
@Composable
internal fun SettingsPartScreen(part: String, go: (Screen) -> Unit, back: () -> Unit) {
    watchStore()
    var pending by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val p = pending
    if (p != null) {
        BreathGate(p.first, onPass = { p.second(); pending = null }, onCancel = { pending = null })
    } else {
        SettingsPart(part, go, back, rememberGuard { pending = it })
    }
}

@Composable
private fun SettingsPart(part: String, go: (Screen) -> Unit, back: () -> Unit, guard: (String, () -> Unit) -> Unit) {
    val context = LocalContext.current
    val title = PARTS.find { it.first == part }?.second ?: "Settings"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        BackHeader(title, back)
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (part) {
                "pauses" -> {
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
                    StepSlider(
                        "Pauses grow", "Each open adds this much to the next pause that day",
                        listOf(0, 1, 2, 3, 5), Store.growSec, { if (it == 0) "off" else "+$it s per open" },
                    ) { v -> if (v < Store.growSec) guard("Making pauses shorter?") { Store.growSec = v } else Store.growSec = v }
                    StepSlider(
                        "A heads-up before a session ends", "A quiet notice and a soft tap",
                        listOf(0, 1, 2, 5), Store.headsUpMin, { if (it == 0) "none" else "$it min before" },
                    ) { Store.headsUpMin = it }
                    StepSlider(
                        "Your day starts at", "When opens and counts start again. Later is kinder to night owls.",
                        (0..6).toList(), Store.dayStartHour, { if (it == 0) "midnight" else "%02d:00".format(it) },
                    ) { Store.dayStartHour = it }
                    StepSlider(
                        "Breathe before loosening a rule", "Only when a change makes things easier, or before a break",
                        listOf(0, 3, 5, 10, 20, 30), Store.guardSec, { if (it == 0) "off" else "$it s" },
                    ) { v -> if (v < Store.guardSec) guard("Making it easier to change your rules?") { Store.guardSec = v } else Store.guardSec = v }

                }
                "atpause" -> {
                    EditableList(
                        title = "Going in to…",
                        subtitle = "Words you can tap at the pause to say what you're going in for.",
                        items = Store.intentions,
                        addLabel = "Add a word",
                        placeholder = "check the news",
                        suggestions = Store.INTENTION_IDEAS,
                    ) { Store.intentions = it }
                    EditableList(
                        title = "Instead",
                        subtitle = "After \"Not now\", [pause] suggests one of these.",
                        items = Store.ideas,
                        addLabel = "Add an idea",
                        placeholder = "call a friend",
                        suggestions = Store.INSTEAD_IDEAS,
                    ) { Store.ideas = it }

                }
                "breathing" -> {
                    ToggleRow(
                        "Breath you can feel",
                        "A soft tap as each breath in and out begins",
                        Store.breathVibration,
                    ) { Store.breathVibration = it }
                    ToggleRow(
                        "A bell at the rest",
                        "A quiet bell as the dots settle into the ensō. Silent when your phone is on silent.",
                        Store.restBell,
                    ) { Store.restBell = it }
                }
                "appearance" -> {
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("The app", style = Type.heading, color = Ink.ink)
                            Choice(
                                options = listOf("light" to "Light", "dark" to "Black"),
                                selected = Store.theme,
                                onSelect = { Store.theme = it },
                            )
                        }
                    }
                    ToggleRow(
                        "Light breathing screen",
                        "Dark dots on soft white. Off: white dots on black.",
                        Store.breatheLight,
                    ) { Store.breatheLight = it }
                }
                "intention" -> {
                    val askForMorning = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        Store.intentionReminder = granted
                        DailyIntention.schedule(context)
                    }
                    ToggleRow("Ask me each morning", "Would you like to set an intention for today?", Store.intentionReminder) { on ->
                        if (on && Build.VERSION.SDK_INT >= 33) {
                            askForMorning.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            Store.intentionReminder = on
                            DailyIntention.schedule(context)
                        }
                    }
                    if (Store.intentionReminder) {
                        val times = (12..22).map { it * 30 } // 06:00 to 11:00
                        StepSlider(
                            "When it comes", "Every morning at",
                            times, times.minByOrNull { kotlin.math.abs(it - Store.intentionTime) } ?: 510,
                            { "%02d:%02d".format(it / 60, it % 60) },
                        ) {
                            Store.intentionTime = it
                            DailyIntention.schedule(context)
                        }
                    }
                    QuietButton("Set today's intention", { go(Screen.Intention) }, Modifier.fillMaxWidth())
                }
                "letter" -> {
                    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        Store.letterOn = granted
                        WeeklyLetter.schedule(context)
                    }
                    ToggleRow("A letter on Sundays", "A few calm sentences about your week, at 10:00", Store.letterOn) { on ->
                        if (on && Build.VERSION.SDK_INT >= 33) {
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            Store.letterOn = on
                            WeeklyLetter.schedule(context)
                        }
                    }
                }
                else -> {
                    Panel(
                        Modifier.clickable(role = Role.Button) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TIP_URL)))
                        },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Buy me a coffee", style = Type.heading, color = Ink.ink)
                            Text("[pause] is free. If it helps you, you can leave a tip.", style = Type.small, color = Ink.muted)
                        }
                    }
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Nothing leaves your phone. No account, no tracking, no ads, and no streaks to keep.",
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
                            TextButton(onClick = { Store.welcomeDone = false }) {
                                Text("See the introduction again", style = Type.body, color = Ink.ink)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
