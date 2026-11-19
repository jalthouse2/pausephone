package com.jalthouse.pausephone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jalthouse.pausephone.data.Store
import com.jalthouse.pausephone.ui.BackHeader
import com.jalthouse.pausephone.ui.Enso
import com.jalthouse.pausephone.ui.Ink
import com.jalthouse.pausephone.ui.PrimaryButton
import com.jalthouse.pausephone.ui.QuietButton
import com.jalthouse.pausephone.ui.StepSlider
import com.jalthouse.pausephone.ui.ToggleRow
import com.jalthouse.pausephone.ui.Type
import com.jalthouse.pausephone.ui.minutesText

private val SIT_MINUTES = listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60)

/** Sit and breathe: a meditation timer that breathes with you and ends with a singing bowl. */
@Composable
internal fun SitScreen(back: () -> Unit) {
    watchStore()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        BackHeader("Breathing session", back)
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Enso(120.dp) }
            Text(
                "Sit for a while. The dots breathe with you, and a singing bowl sounds when the time is up.",
                style = Type.body,
                color = Ink.muted,
            )
            StepSlider(
                "How long", "Your screen stays on while you sit",
                SIT_MINUTES, SIT_MINUTES.minByOrNull { kotlin.math.abs(it - Store.sitMin) } ?: 5, { minutesText(it) },
            ) { Store.sitMin = it }
            StepSlider(
                "Breath length", "One breath in, one breath out",
                BREATHS, Store.sitBreath, { "$it s each way" },
            ) { Store.sitBreath = it }
            Text("An intention", style = Type.heading, color = Ink.ink, modifier = Modifier.padding(top = 10.dp))
            Text(
                "Something to hold while you sit. It stays quietly on the screen. Optional.",
                style = Type.small,
                color = Ink.muted,
            )
            IntentionPicker(Store.sitIntention) { Store.sitIntention = it }
            ToggleRow("A bowl to begin", "A soft bowl as you start. The end always has one.", Store.sitStartBowl) {
                Store.sitStartBowl = it
            }
            PrimaryButton(
                "Begin",
                { context.startActivity(PauseActivity.launchIntent(context, PauseMode.FREE, null, minutes = Store.sitMin)) },
                Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            QuietButton(
                "Just one breath",
                { context.startActivity(PauseActivity.launchIntent(context, PauseMode.FREE, null)) },
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntentionPicker(current: String, onChange: (String) -> Unit) {
    var writing by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val options = (listOf("") + Store.SIT_INTENTIONS + listOfNotNull(current.takeIf { it.isNotBlank() && it !in Store.SIT_INTENTIONS }))
        options.forEach { word ->
            val on = word == current
            Text(
                if (word.isEmpty()) "none" else word,
                style = Type.small.copy(fontSize = 14.sp),
                color = if (on) Ink.paper else Ink.ink,
                modifier = Modifier
                    .background(if (on) Ink.ink else Ink.paper, RoundedCornerShape(18.dp))
                    .border(1.dp, if (on) Ink.ink else Ink.track, RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { onChange(word) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        Text(
            "+ your own",
            style = Type.small.copy(fontSize = 14.sp),
            color = Ink.ink,
            modifier = Modifier
                .border(1.dp, Ink.track, RoundedCornerShape(18.dp))
                .clickable(role = Role.Button) { writing = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
    if (writing) {
        NameDialog("Your intention", current, ideas = emptyList(), placeholder = "be patient with myself") { text ->
            writing = false
            if (text != null) onChange(text.lowercase())
        }
    }
}
