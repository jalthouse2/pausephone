package com.jalthouse.pausephone

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jalthouse.pausephone.data.Kind
import com.jalthouse.pausephone.data.Rule
import com.jalthouse.pausephone.data.Store
import com.jalthouse.pausephone.data.Usage
import com.jalthouse.pausephone.data.durationText
import com.jalthouse.pausephone.ui.BackHeader
import com.jalthouse.pausephone.ui.Ink
import com.jalthouse.pausephone.ui.LetterTile
import com.jalthouse.pausephone.ui.PrimaryButton
import com.jalthouse.pausephone.ui.QuietButton
import com.jalthouse.pausephone.ui.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Apps that most often pull people in, shown first when they're installed. */
internal val USUAL_APPS = listOf(
    "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
    "com.google.android.youtube", "com.twitter.android", "com.facebook.katana",
    "com.reddit.frontpage", "com.snapchat.android", "com.instagram.barcelona",
    "com.linkedin.android", "com.netflix.mediaclient", "com.pinterest",
    "tv.twitch.android.app", "com.tinder", "com.bumble.app", "com.google.android.apps.youtube.music",
    "com.amazon.mShop.android.shopping", "com.einnovation.temu", "com.zzkko",
)

/** Websites that most often pull people in. */
internal val USUAL_SITES = listOf(
    "instagram.com", "youtube.com", "tiktok.com", "x.com", "facebook.com", "reddit.com",
    "linkedin.com", "threads.net", "netflix.com", "twitch.tv", "pinterest.com",
    "news.google.com", "lemonde.fr", "nytimes.com", "theguardian.com", "amazon.com",
)

/**
 * Choose apps and sites in one place: two tabs, a search box, the usual culprits first,
 * a checkbox on each. Done saves the choices: new ones start with your usual pause,
 * and ones you untick are removed.
 */
@Composable
internal fun ChooseScreen(startOnSites: Boolean, onDone: () -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    var sites by remember { mutableStateOf(startOnSites) }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var usage by remember { mutableStateOf<Usage.Summary?>(null) }
    var addingSite by remember { mutableStateOf(false) }
    val existing = remember { Store.rules() }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(existing.map { it.id }) } }
    val extraSites = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(context) }
        usage = withContext(Dispatchers.IO) { Usage.lastDays(context, 7) }
    }

    fun toggle(id: String) {
        if (id in chosen) chosen.remove(id) else chosen.add(id)
    }

    fun save() {
        val labels = apps.orEmpty().associate { it.pkg to it.label }
        existing.filter { it.id !in chosen }.forEach { Store.removeRule(it.id) }
        chosen.filter { id -> existing.none { it.id == id } }.forEach { id ->
            val isSite = id in USUAL_SITES || id in extraSites || labels[id] == null
            Store.saveRule(
                Rule(
                    id = id,
                    label = labels[id] ?: id,
                    kind = if (isSite) Kind.SITE else Kind.APP,
                    pauseSec = Store.defaultPause,
                ),
            )
        }
        onDone()
    }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        BackHeader(if (sites) "Choose sites" else "Choose apps", back)
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segment("Apps", !sites) { sites = false; query = "" }
            Segment("Sites", sites) { sites = true; query = "" }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(if (sites) "Search sites" else "Search your apps", style = Type.body, color = Ink.muted) },
            textStyle = Type.body,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink.ink,
                unfocusedBorderColor = Ink.track,
                cursorColor = Ink.ink,
                focusedTextColor = Ink.ink,
                unfocusedTextColor = Ink.ink,
            ),
        )
        val q = query.trim()
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            if (sites) {
                val all = (USUAL_SITES + existing.filter { it.kind == Kind.SITE }.map { it.id } + extraSites).distinct()
                val shown = all.filter { q.isEmpty() || it.contains(q, ignoreCase = true) }
                item { Section("Usual culprits") }
                items(shown, key = { "s:$it" }) { domain ->
                    CheckRow(domain, null, { LetterTile(domain) }, domain in chosen) { toggle(domain) }
                }
                item {
                    QuietButton(
                        "Add another site",
                        { addingSite = true },
                        Modifier.fillMaxWidth().padding(vertical = 12.dp).height(48.dp),
                    )
                }
            } else {
                val list = apps
                if (list == null) {
                    item { Text("Finding your apps…", style = Type.small, color = Ink.muted, modifier = Modifier.padding(vertical = 12.dp)) }
                } else {
                    val days = usage?.days ?: 1
                    val timeOf = usage?.perApp?.toMap().orEmpty()
                    val match = list.filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) }
                    // Usual culprits: well-known ones you have, plus your most-used apps.
                    val usualIds = (USUAL_APPS + usage?.perApp?.take(6)?.map { it.first }.orEmpty()).toSet()
                    val usual = match.filter { it.pkg in usualIds }
                        .sortedByDescending { timeOf[it.pkg] ?: 0L }
                    val rest = match.filter { it.pkg !in usualIds }
                    if (usual.isNotEmpty()) item { Section("Usual culprits") }
                    items(usual, key = { "u:${it.pkg}" }) { app ->
                        AppRow(app, timeOf[app.pkg]?.let { durationText(it / days) + " a day" }, app.pkg in chosen) { toggle(app.pkg) }
                    }
                    if (rest.isNotEmpty()) item { Section("All apps") }
                    items(rest, key = { "a:${it.pkg}" }) { app ->
                        AppRow(app, timeOf[app.pkg]?.let { durationText(it / days) + " a day" }, app.pkg in chosen) { toggle(app.pkg) }
                    }
                }
            }
        }
        PrimaryButton("Done", ::save, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
    }

    if (addingSite) {
        AddSiteDialog(onDone = { domain ->
            addingSite = false
            if (domain != null) {
                if (domain !in extraSites && domain !in USUAL_SITES) extraSites.add(domain)
                if (domain !in chosen) chosen.add(domain)
            }
        })
    }
}

@Composable
private fun Segment(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = Type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        color = if (on) Ink.paper else Ink.ink,
        modifier = Modifier
            .background(if (on) Ink.ink else Ink.paper, RoundedCornerShape(22.dp))
            .border(1.dp, if (on) Ink.ink else Ink.track, RoundedCornerShape(22.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    )
}

@Composable
private fun Section(title: String) {
    Text(title, style = Type.heading, color = Ink.ink, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun AppRow(app: AppEntry, sub: String?, on: Boolean, onToggle: () -> Unit) {
    CheckRow(
        app.label,
        sub,
        {
            val icon = app.icon
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp)) else LetterTile(app.label)
        },
        on,
        onToggle,
    )
}

@Composable
private fun CheckRow(title: String, sub: String?, leading: @Composable () -> Unit, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body, color = Ink.ink)
            if (sub != null) Text(sub, style = Type.small, color = Ink.muted)
        }
        Box(
            Modifier
                .size(24.dp)
                .background(if (on) Ink.ink else Ink.paper, RoundedCornerShape(6.dp))
                .border(1.5.dp, if (on) Ink.ink else Ink.track, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Box(Modifier.size(8.dp).background(Ink.paper, RoundedCornerShape(2.dp)))
        }
    }
}
