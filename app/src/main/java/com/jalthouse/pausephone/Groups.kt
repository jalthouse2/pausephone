package com.jalthouse.pausephone

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jalthouse.pausephone.data.Group
import com.jalthouse.pausephone.data.Kind
import com.jalthouse.pausephone.data.Store
import com.jalthouse.pausephone.ui.BackHeader
import com.jalthouse.pausephone.ui.Ink
import com.jalthouse.pausephone.ui.LetterTile
import com.jalthouse.pausephone.ui.Panel
import com.jalthouse.pausephone.ui.PrimaryButton
import com.jalthouse.pausephone.ui.QuietButton
import com.jalthouse.pausephone.ui.StepSlider
import com.jalthouse.pausephone.ui.ToggleRow
import com.jalthouse.pausephone.ui.Type
import com.jalthouse.pausephone.ui.minutesText

private val NAME_IDEAS = listOf("Social", "Video", "News", "Work", "Games", "Shopping", "Messages")

// ---------------------------------------------------------------- summaries

private fun beforeText(g: Group) =
    (if (g.pauseSec == 0) "No pause" else "${g.pauseSec} s pause") + " · ${g.breathSec} s breaths"

private fun limitsText(g: Group): String {
    val parts = mutableListOf<String>()
    if (g.opensPerDay > 0) parts += "${g.opensPerDay} ${if (g.opensPerDay == 1) "open" else "opens"} a day"
    if (g.sessionMin > 0) parts += minutesText(g.sessionMin) + " sessions"
    if (g.breatherMin > 0) parts += "a breather after " + minutesText(g.breatherMin)
    return if (parts.isEmpty()) "No limits" else parts.joinToString(" · ")
}

private fun blockingText(g: Group) = when {
    g.blocked -> "Blocked completely"
    g.blockTimes.isNotEmpty() -> "Rests " + g.blockTimes.joinToString(", ") { "${it.start} to ${it.end}" }
    else -> "Off"
}

private fun summary(g: Group) = when {
    g.blocked -> "Blocked completely"
    else -> listOf(beforeText(g).substringBefore(" ·"), limitsText(g)).filter { it != "No limits" }.joinToString(" · ")
}

// ---------------------------------------------------------------- the Apps tab

/** Your groups. Each has a name, its apps and sites, and one set of rules. */
@Composable
internal fun GroupsScreen(go: (Screen) -> Unit) {
    watchStore()
    val groups = Store.groups()
    var picking by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<Boolean?>(null) } // null = not naming; else open sites afterwards?

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TabTitle("Apps and sites")
        Text(
            "Apps and sites in a group share one set of rules. Opens and sessions still count for each app.",
            style = Type.small,
            color = Ink.muted,
        )
        if (groups.isEmpty()) {
            Text("No groups yet. Start one with the apps that pull you in.", style = Type.body, color = Ink.ink)
        }
        groups.forEach { g -> GroupCard(g) { go(Screen.GroupEdit(g.id)) } }
        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            "Add apps and sites",
            { if (groups.isEmpty()) naming = false else picking = true },
            Modifier.fillMaxWidth(),
        )
        QuietButton("New group", { naming = false }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
    }

    if (picking) {
        PickGroupDialog(
            groups = groups,
            onPick = { id -> picking = false; go(Screen.Choose(id, sites = false)) },
            onNew = { picking = false; naming = false },
            onDismiss = { picking = false },
        )
    }
    if (naming != null) {
        NameDialog(
            title = "New group",
            initial = "",
            onDone = { name ->
                naming = null
                if (name != null) {
                    val g = Store.newGroup(name)
                    go(Screen.GroupEdit(g.id))
                    go(Screen.Choose(g.id, sites = false))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupCard(g: Group, onClick: () -> Unit) {
    Panel(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(g.name, style = Type.heading, color = Ink.ink)
                    Text(summary(g).ifBlank { "No pause" }, style = Type.small, color = Ink.muted)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Ink.muted)
            }
            if (g.members.isEmpty()) {
                Text("No apps or sites yet", style = Type.small, color = Ink.muted)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    g.members.take(7).forEach { LetterTile(it.label) }
                    if (g.members.size > 7) {
                        Text("+${g.members.size - 7}", style = Type.body, color = Ink.muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PickGroupDialog(groups: List<Group>, onPick: (String) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.paper,
        title = { Text("Add to which group?", style = Type.heading, color = Ink.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                groups.forEach { g ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(g.id) }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = Type.body, color = Ink.ink)
                            Text(summary(g).ifBlank { "No pause" }, style = Type.small, color = Ink.muted)
                        }
                    }
                    HorizontalDivider(color = Ink.line)
                }
                Text(
                    "New group…",
                    style = Type.body.copy(fontWeight = FontWeight.Medium),
                    color = Ink.ink,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onNew).padding(vertical = 14.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = Type.button, color = Ink.muted) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NameDialog(
    title: String,
    initial: String,
    ideas: List<String> = NAME_IDEAS,
    placeholder: String = "Social",
    onDone: (String?) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = Ink.paper,
        title = { Text(title, style = Type.heading, color = Ink.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(30) },
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ideas.forEach { idea ->
                        Text(
                            idea,
                            style = Type.small.copy(fontSize = 14.sp),
                            color = Ink.ink,
                            modifier = Modifier
                                .border(1.dp, Ink.track, RoundedCornerShape(18.dp))
                                .clickable(role = Role.Button) { text = idea }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(text.trim()) }) {
                Text("Save", style = Type.button, color = Ink.ink)
            }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel", style = Type.button, color = Ink.muted) } },
    )
}

// ---------------------------------------------------------------- one group

/** A group: its members, then its rules in three parts you tap into. */
@Composable
internal fun GroupScreen(id: String, go: (Screen) -> Unit, back: () -> Unit) {
    watchStore()
    val g = Store.group(id)
    if (g == null) {
        LaunchedEffect(id) { back() }
        return
    }
    var renaming by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val apps = g.members.filter { it.kind == Kind.APP }
    val sites = g.members.filter { it.kind == Kind.SITE }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        BackHeader(g.name, back)
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MemberLine("Apps", apps.map { it.label }) { go(Screen.Choose(id, sites = false)) }
                    HorizontalDivider(color = Ink.line)
                    MemberLine("Sites", sites.map { it.label }) { go(Screen.Choose(id, sites = true)) }
                }
            }
            Part("Before opening", beforeText(g)) { go(Screen.GroupPart(id, "before")) }
            Part("Limits", limitsText(g)) { go(Screen.GroupPart(id, "limits")) }
            Part("Blocking", blockingText(g)) { go(Screen.GroupPart(id, "blocking")) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { renaming = true }) { Text("Rename", style = Type.button, color = Ink.ink) }
                TextButton(onClick = { removing = true }) { Text("Remove group", style = Type.button, color = Ink.muted) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (renaming) {
        NameDialog("Rename group", g.name) { name ->
            renaming = false
            if (!name.isNullOrBlank()) Store.saveGroup(g.copy(name = name))
        }
    }
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            containerColor = Ink.paper,
            title = { Text("Remove ${g.name}?", style = Type.heading, color = Ink.ink) },
            text = { Text("Its apps and sites will open without a pause.", style = Type.body, color = Ink.muted) },
            confirmButton = {
                TextButton(onClick = { removing = false; Store.removeGroup(id); back() }) {
                    Text("Remove", style = Type.button, color = Ink.ink)
                }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text("Keep", style = Type.button, color = Ink.muted) } },
        )
    }
}

@Composable
private fun MemberLine(title: String, names: List<String>, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onEdit).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Type.heading, color = Ink.ink)
            Text(
                if (names.isEmpty()) "None yet. Tap to add." else names.joinToString(", "),
                style = Type.small,
                color = Ink.muted,
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Edit $title", tint = Ink.muted)
    }
}

@Composable
private fun Part(title: String, value: String, onClick: () -> Unit) {
    Panel(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = Type.heading, color = Ink.ink)
                Text(value, style = Type.small, color = Ink.muted)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Ink.muted)
        }
    }
}

// ---------------------------------------------------------------- one part of a group

@Composable
internal fun GroupPartScreen(id: String, part: String, back: () -> Unit) {
    watchStore()
    val g = Store.group(id)
    if (g == null) {
        LaunchedEffect(id) { back() }
        return
    }
    fun save(n: Group) = Store.saveGroup(n)
    val (title, line) = when (part) {
        "before" -> "Before opening" to "How [pause] greets ${g.name} each time you open one."
        "limits" -> "Limits" to "How often and how long, for each app or site in ${g.name}."
        else -> "Blocking" to "When ${g.name} rests."
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        BackHeader(title, back)
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(line, style = Type.body, color = Ink.muted, modifier = Modifier.padding(bottom = 8.dp))
            when (part) {
                "before" -> {
                    StepSlider(
                        "Pause before opening", "Breathing before it opens",
                        PAUSES, g.pauseSec, { if (it == 0) "none" else "$it s" },
                    ) { save(g.copy(pauseSec = it)) }
                    StepSlider(
                        "Breath length", "One breath in, one breath out",
                        BREATHS, g.breathSec, { "$it s each way" },
                    ) { save(g.copy(breathSec = it)) }
                }
                "limits" -> {
                    if (g.opensPerDay > 0 && g.sessionMin > 0 && !g.blocked) {
                        Panel {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Up to ${minutesText(g.opensPerDay * g.sessionMin)} a day per app",
                                    style = Type.stat.copy(fontSize = 22.sp),
                                    color = Ink.ink,
                                )
                                Text(
                                    "${g.opensPerDay} ${if (g.opensPerDay == 1) "open" else "opens"} × ${minutesText(g.sessionMin)}",
                                    style = Type.small,
                                    color = Ink.muted,
                                )
                            }
                        }
                    }
                    StepSlider(
                        "Opens per day", "After that it stays closed until tomorrow",
                        OPENS, if (g.blocked) BLOCKED else g.opensPerDay,
                        { when (it) { BLOCKED -> "none · blocked"; 0 -> "no limit"; 1 -> "1 open"; else -> "$it opens" } },
                    ) {
                        if (it == BLOCKED) save(g.copy(blocked = true)) else save(g.copy(blocked = false, opensPerDay = it))
                    }
                    StepSlider(
                        "Session length", "Then the breathing cloud returns",
                        SESSIONS, g.sessionMin, { if (it == 0) "no limit" else minutesText(it) },
                    ) { save(g.copy(sessionMin = it)) }
                    StepSlider(
                        "A breather along the way", "After this much time in the app",
                        BREATHERS, g.breatherMin, { if (it == 0) "never" else minutesText(it) },
                    ) { save(g.copy(breatherMin = it)) }
                }
                else -> {
                    ToggleRow(
                        "Block completely",
                        "Closed until you turn this off",
                        g.blocked,
                    ) { save(g.copy(blocked = it)) }
                    BlockTimes(g.name, g.blockTimes) { save(g.copy(blockTimes = it)) }
                }
            }
            PrimaryButton("Done", back, Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp))
        }
    }
}
