package com.jalthouse.pausephone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jalthouse.pausephone.data.QUOTES
import com.jalthouse.pausephone.data.Rule
import com.jalthouse.pausephone.data.Sessions
import com.jalthouse.pausephone.data.Store
import com.jalthouse.pausephone.ui.DotCloud
import com.jalthouse.pausephone.ui.Ink
import com.jalthouse.pausephone.ui.Manrope
import com.jalthouse.pausephone.ui.PauseTheme
import com.jalthouse.pausephone.ui.PrimaryButton
import com.jalthouse.pausephone.ui.QuietButton
import com.jalthouse.pausephone.ui.Type
import com.jalthouse.pausephone.ui.minutesText
import kotlin.math.ceil

enum class PauseMode {
    /** Before opening an app or site. */
    ENTRY,
    /** A breather along the way, partway through a session. */
    BREATHER,
    /** The session length is up. */
    SESSION_END,
    /** Blocked, night hours, or no opens left today. */
    BLOCKED,
    /** Right after unlocking the phone. */
    UNLOCK,
    /** "Breathe now" from the app. */
    FREE,
}

private data class PauseRequest(val mode: PauseMode, val rule: Rule?, val reason: String?, val stamp: Long)

class PauseActivity : ComponentActivity() {

    private var request by mutableStateOf<PauseRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        request = parse(intent)
        onBackPressedDispatcher.addCallback(this) { onBack() }
        setContent {
            Ink.breatheLight = Store.breatheLight
            val view = LocalView.current
            SideEffect {
                window.statusBarColor = Ink.night.toArgb()
                window.navigationBarColor = Ink.night.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = Ink.breatheLight
                    isAppearanceLightNavigationBars = Ink.breatheLight
                }
            }
            PauseTheme {
                request?.let { req -> PauseScreen(req, actions(req)) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        request = parse(intent)
    }

    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onStop() {
        super.onStop()
        visible = false
    }

    private fun parse(i: Intent): PauseRequest {
        val mode = runCatching { PauseMode.valueOf(i.getStringExtra(EXTRA_MODE) ?: "") }.getOrDefault(PauseMode.FREE)
        val rule = i.getStringExtra(EXTRA_ID)?.let { Store.rule(it) }
        when (mode) {
            PauseMode.ENTRY, PauseMode.UNLOCK -> Store.bump("pauses")
            PauseMode.BREATHER -> Store.bump("breathers")
            else -> {}
        }
        return PauseRequest(mode, rule, i.getStringExtra(EXTRA_REASON), System.nanoTime())
    }

    private fun onBack() {
        when (request?.mode) {
            PauseMode.ENTRY -> notNow()
            PauseMode.BLOCKED, PauseMode.SESSION_END -> goHome()
            else -> finish()
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    private fun notNow() {
        Store.bump("letGo")
        goHome()
    }

    private fun goIn(rule: Rule) {
        Store.recordOpen(rule.id)
        Sessions.start(rule)
        finish() // reveals the app or browser underneath
    }

    private fun actions(req: PauseRequest) = PauseActions(
        notNow = ::notNow,
        goIn = { req.rule?.let(::goIn) ?: finish() },
        goHome = ::goHome,
        close = ::finish,
    )

    companion object {
        @Volatile
        var visible = false

        private const val EXTRA_MODE = "mode"
        private const val EXTRA_ID = "id"
        private const val EXTRA_REASON = "reason"

        fun launchIntent(context: Context, mode: PauseMode, id: String?, reason: String? = null): Intent =
            Intent(context, PauseActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_REASON, reason)
    }
}

private class PauseActions(
    val notNow: () -> Unit,
    val goIn: () -> Unit,
    val goHome: () -> Unit,
    val close: () -> Unit,
)

private fun ordinal(n: Int) = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

@Composable
private fun PauseScreen(req: PauseRequest, act: PauseActions) {
    val rule = req.rule
    val label = rule?.label ?: ""
    val breath = when {
        rule != null -> rule.breathSec
        req.mode == PauseMode.UNLOCK -> Store.unlockBreathSec
        else -> 4
    }
    val pauseSec = when (req.mode) {
        PauseMode.ENTRY -> rule?.pauseSec ?: 0
        PauseMode.UNLOCK -> Store.unlockPauseSec
        PauseMode.BREATHER -> breath * 2
        else -> 0
    }

    // One clock drives the cloud, the words, the countdown and the quotes.
    val clock = remember(req.stamp) { mutableFloatStateOf(0f) }
    LaunchedEffect(req.stamp) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { clock.floatValue = (it - start) / 1_000_000_000f }
    }
    val cycle = breath * 2f
    val breathingIn by remember(req.stamp, breath) { derivedStateOf { clock.floatValue % cycle < breath } }
    val secondsLeft by remember(req.stamp, pauseSec) { derivedStateOf { ceil(pauseSec - clock.floatValue).toInt() } }
    val quoteWindow = cycle * 2f
    val quoteIndex by remember(req.stamp, breath) {
        derivedStateOf { (clock.floatValue / quoteWindow).toInt() % QUOTES.size }
    }
    val quoteShown by remember(req.stamp, breath) {
        derivedStateOf {
            val w = clock.floatValue % quoteWindow
            w > 0.6f && w < quoteWindow - 0.9f
        }
    }
    val quoteAlpha by animateFloatAsState(if (quoteShown) 1f else 0f, tween(800), label = "quote")
    val waiting = secondsLeft > 0

    val top = when (req.mode) {
        PauseMode.ENTRY -> "opening $label"
        PauseMode.BREATHER -> "a breather along the way"
        PauseMode.SESSION_END -> "your ${rule?.sessionMin?.let(::minutesText) ?: ""} with $label is complete"
        PauseMode.BLOCKED -> when (req.reason) {
            "night" -> "$label is resting until ${Store.nightEnd}"
            "limit" -> "that's all the opens for $label today"
            else -> "$label is resting"
        }
        PauseMode.UNLOCK -> "welcome back"
        PauseMode.FREE -> "breathe"
    }
    val phase = when {
        (req.mode == PauseMode.ENTRY || req.mode == PauseMode.UNLOCK) && !waiting -> "ready when you are"
        breathingIn -> "breathe in"
        else -> "breathe out"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.night)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(top, style = Type.body.copy(fontSize = 15.sp), color = Ink.mistMuted, textAlign = TextAlign.Center)
        DotCloud(
            elapsed = { clock.floatValue },
            breathSec = breath,
            modifier = Modifier.padding(top = 28.dp).size(300.dp),
        )
        Text(
            phase,
            style = Type.body.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 2.sp),
            color = Ink.mist,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            if (waiting) "$secondsLeft s" else "",
            style = Type.small.copy(fontSize = 14.sp),
            color = Ink.mistMuted,
            modifier = Modifier.padding(top = 6.dp).heightIn(min = 20.dp),
        )
        val quote = QUOTES[quoteIndex]
        Column(
            Modifier.padding(top = 20.dp).widthIn(max = 320.dp).heightIn(min = 110.dp).alpha(quoteAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                quote.text,
                fontFamily = Manrope,
                fontSize = 17.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 25.sp,
                color = Ink.mist.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
            Text(quote.by, style = Type.small, color = Ink.mistMuted, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.weight(1f))

        if (req.mode == PauseMode.ENTRY && rule != null) {
            val used = Store.opensToday(rule.id)
            val parts = mutableListOf(ordinal(used + 1) + " open today")
            if (rule.opensPerDay > 0) parts += "${(rule.opensPerDay - used - 1).coerceAtLeast(0)} left"
            if (rule.sessionMin > 0) parts += minutesText(rule.sessionMin) + " session"
            Text(
                parts.joinToString(" · "),
                style = Type.small,
                color = Ink.mistMuted,
                modifier = Modifier.padding(bottom = 18.dp),
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (req.mode) {
                PauseMode.ENTRY -> {
                    PrimaryButton("Not now", act.notNow, Modifier.weight(1f), dark = true)
                    if (!waiting) QuietButton("Open $label", act.goIn, Modifier.weight(1f), dark = true)
                    else Spacer(Modifier.weight(1f))
                }
                PauseMode.SESSION_END -> {
                    PrimaryButton("Close $label", act.goHome, Modifier.weight(1f), dark = true)
                    val canGoOn = rule != null && (rule.opensPerDay == 0 || Store.opensToday(rule.id) < rule.opensPerDay)
                    if (canGoOn) QuietButton("Another session", act.goIn, Modifier.weight(1f), dark = true)
                }
                PauseMode.BREATHER -> {
                    QuietButton("Close $label", act.goHome, Modifier.weight(1f), dark = true)
                    if (!waiting) PrimaryButton("Continue", act.close, Modifier.weight(1f), dark = true)
                    else Spacer(Modifier.weight(1f))
                }
                PauseMode.BLOCKED -> PrimaryButton("Back home", act.goHome, Modifier.weight(1f), dark = true)
                PauseMode.UNLOCK -> {
                    if (!waiting) PrimaryButton("Continue", act.close, Modifier.weight(1f), dark = true)
                    else Spacer(Modifier.height(56.dp))
                }
                PauseMode.FREE -> PrimaryButton("Done", act.close, Modifier.weight(1f), dark = true)
            }
        }
    }
}
