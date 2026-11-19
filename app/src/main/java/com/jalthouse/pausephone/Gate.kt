package com.jalthouse.pausephone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jalthouse.pausephone.data.Store
import com.jalthouse.pausephone.ui.Breath
import com.jalthouse.pausephone.ui.DotCloud
import com.jalthouse.pausephone.ui.Ink
import com.jalthouse.pausephone.ui.PrimaryButton
import com.jalthouse.pausephone.ui.QuietButton
import com.jalthouse.pausephone.ui.Type
import kotlin.math.ceil

/**
 * "Breathe before loosening a rule": a short breath before changing rules or taking a break,
 * so loosening them is a choice, not an impulse.
 */
@Composable
fun BreathGate(reason: String, onPass: () -> Unit, onCancel: () -> Unit) {
    val seconds = Store.guardSec
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { clock.floatValue = (it - start) / 1_000_000_000f }
    }
    val rhythm = remember { Breath(4f) }
    val word by remember { derivedStateOf { rhythm.word(clock.floatValue) } }
    val left by remember { derivedStateOf { ceil(seconds - clock.floatValue).toInt() } }
    BackHandler(onBack = onCancel)

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        Text(
            reason,
            style = Type.title.copy(fontSize = 26.sp, lineHeight = 32.sp),
            color = Ink.ink,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "Take a breath first. If you still want to, go ahead.",
            style = Type.body,
            color = Ink.muted,
            modifier = Modifier.padding(top = 10.dp),
        )
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            DotCloud(elapsed = { clock.floatValue }, breathSec = 4, color = Ink.ink, modifier = Modifier.size(260.dp))
        }
        Text(
            if (left > 0) word else "ready when you are",
            style = Type.body.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 2.sp),
            color = Ink.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (left > 0) "$left s" else "",
            style = Type.small,
            color = Ink.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 20.dp),
        )
        if (left <= 0) {
            PrimaryButton("Continue", onPass, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        QuietButton("Never mind", onCancel, Modifier.fillMaxWidth())
    }
}

/** Shows [content] after a [BreathGate], when "Breathe before loosening a rule" is on. */
@Composable
fun Guarded(reason: String, onCancel: () -> Unit, content: @Composable () -> Unit) {
    var passed by remember { mutableStateOf(Store.guardSec == 0) }
    if (passed) content() else BreathGate(reason, onPass = { passed = true }, onCancel = onCancel)
}
