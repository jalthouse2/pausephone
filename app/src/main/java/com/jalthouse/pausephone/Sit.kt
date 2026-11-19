package com.jalthouse.pausephone

import androidx.compose.foundation.layout.Arrangement
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
        BackHeader("Sit and breathe", back)
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Enso(120.dp) }
            Text(
                "The dots breathe with you. When the time is up, a singing bowl sounds.",
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
