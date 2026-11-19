package com.jalthouse.pausephone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.jalthouse.pausephone.data.Quote
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
import com.jalthouse.pausephone.ui.Breath
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
    /** After "Not now": something else you could do. The idea travels in the reason. */
    LET_GO,
}

private data class PauseRequest(val mode: PauseMode, val rule: Rule?, val reason: String?, val stamp: Long, val minutes: Int = 0)

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
        return PauseRequest(mode, rule, i.getStringExtra(EXTRA_REASON), System.nanoTime(), i.getIntExtra(EXTRA_MINUTES, 0))
    }

    private fun onBack() {
        when (request?.mode) {
            PauseMode.ENTRY -> notNow()
            PauseMode.BLOCKED, PauseMode.SESSION_END, PauseMode.LET_GO -> goHome()
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
        val req = request
        Store.recordLetGo(req?.rule?.id)
        val ideas = Store.ideas
        if (req == null || ideas.isEmpty()) goHome()
        else request = req.copy(mode = PauseMode.LET_GO, reason = ideas.random())
    }

    private fun goIn(rule: Rule, intent: String?) {
        Store.recordOpen(rule.id, intent)
        Sessions.start(rule)
        finish() // reveals the app or browser underneath
    }

    private fun actions(req: PauseRequest) = PauseActions(
        notNow = ::notNow,
        goIn = { intent -> req.rule?.let { goIn(it, intent) } ?: finish() },
        goHome = ::goHome,
        close = ::finish,
    )

    companion object {
        @Volatile
        var visible = false

        private const val EXTRA_MODE = "mode"
        private const val EXTRA_ID = "id"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_MINUTES = "minutes"

        fun launchIntent(context: Context, mode: PauseMode, id: String?, reason: String? = null, minutes: Int = 0): Intent =
            Intent(context, PauseActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_REASON, reason)
                .putExtra(EXTRA_MINUTES, minutes)
    }
}

private class PauseActions(
    val notNow: () -> Unit,
    val goIn: (intent: String?) -> Unit,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PauseScreen(req: PauseRequest, act: PauseActions) {
    val rule = req.rule
    val label = rule?.label ?: ""
    val breath = when {
        rule != null -> rule.breathSec
        req.mode == PauseMode.UNLOCK -> Store.unlockBreathSec
        req.minutes > 0 -> Store.sitBreath
        else -> 4
    }
    val pauseSec = when (req.mode) {
        PauseMode.ENTRY -> rule?.let { (it.pauseSec + Store.growSec * Store.opensToday(it.id)).coerceAtMost(60) } ?: 0
        PauseMode.UNLOCK -> Store.unlockPauseSec
        PauseMode.BREATHER -> kotlin.math.ceil(Breath(breath.toFloat()).cycle).toInt()
        else -> 0
    }

    // One clock drives the cloud, the words, the countdown and the quotes.
    val clock = remember(req.stamp) { mutableFloatStateOf(0f) }
    LaunchedEffect(req.stamp) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { clock.floatValue = (it - start) / 1_000_000_000f }
    }
    val rhythm = remember(breath) { Breath(breath.toFloat()) }
    val cycle = rhythm.cycle
    val breathWord by remember(req.stamp, breath) { derivedStateOf { rhythm.word(clock.floatValue) } }
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

    // A timed sit: a bowl to begin (if you like), a bowl at the end.
    val sitSec = req.minutes * 60
    val sitting = req.mode == PauseMode.FREE && sitSec > 0
    val sitLeft by remember(req.stamp, sitSec) { derivedStateOf { ceil(sitSec - clock.floatValue).toInt() } }
    val sitOver = sitting && sitLeft <= 0

    val top = when (req.mode) {
        PauseMode.ENTRY -> "opening $label"
        PauseMode.BREATHER -> "a breather along the way"
        PauseMode.SESSION_END -> "your ${rule?.sessionMin?.let(::minutesText) ?: ""} with $label is complete"
        PauseMode.BLOCKED -> when {
            req.reason?.startsWith("until:") == true -> "$label is resting until ${req.reason.removePrefix("until:")}"
            req.reason == "limit" -> "that's all the opens for $label today"
            else -> "$label is resting"
        }
        PauseMode.UNLOCK -> "welcome back"
        PauseMode.FREE -> when {
            sitOver -> "your sit is complete"
            sitting -> "breathe · ${minutesText(req.minutes)}"
            else -> "breathe"
        }
        PauseMode.LET_GO -> "$label can wait"
    }
    val context = LocalContext.current
    val bell = rememberBell(context)
    val bowl = rememberBowl(context)
    val view = LocalView.current
    DisposableEffect(sitting) {
        view.keepScreenOn = sitting
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(req.stamp) { if (sitting && Store.sitStartBowl) bowl.play(0.45f) }
    LaunchedEffect(sitOver) {
        if (sitOver) {
            bowl.play(0.9f)
            Store.bump("sits")
        }
    }
    LaunchedEffect(breathWord) {
        when (breathWord) {
            "breathe in" -> if (Store.breathVibration) tap(context, 70)
            "breathe out" -> if (Store.breathVibration) tap(context, 35)
            "rest" -> if (Store.restBell) bell.play()
        }
    }
    var intent by remember(req.stamp) { mutableStateOf<String?>(null) }

    val phase = when {
        (req.mode == PauseMode.ENTRY || req.mode == PauseMode.UNLOCK) && !waiting -> "ready when you are"
        sitOver -> "rest"
        else -> breathWord
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
            when {
                sitting && !sitOver -> "%d:%02d".format(sitLeft / 60, sitLeft % 60)
                waiting -> "$secondsLeft s"
                else -> ""
            },
            style = Type.small.copy(fontSize = 14.sp),
            color = Ink.mistMuted,
            modifier = Modifier.padding(top = 6.dp).heightIn(min = 20.dp),
        )
        val intention = Store.sitIntention
        val quote = if (req.mode == PauseMode.LET_GO) {
            Quote("Maybe ${req.reason ?: "step outside"}.", "instead")
        } else if (sitting && intention.isNotBlank()) {
            Quote(intention, "your intention")
        } else {
            QUOTES[quoteIndex]
        }
        Column(
            Modifier
                .padding(top = 20.dp)
                .widthIn(max = 320.dp)
                .heightIn(min = 110.dp)
                .alpha(
                    when {
                        req.mode == PauseMode.LET_GO -> 1f
                        sitting && intention.isNotBlank() -> 1f
                        sitting && !sitOver -> 0f
                        else -> quoteAlpha
                    },
                ),
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

        val intentions = Store.intentions
        if (req.mode == PauseMode.ENTRY && !waiting && intentions.isNotEmpty()) {
            Text("going in to…", style = Type.small, color = Ink.mistMuted, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            ) {
                intentions.forEach { word ->
                    val on = word == intent
                    Text(
                        word,
                        style = Type.small.copy(fontSize = 14.sp),
                        color = if (on) Ink.night else Ink.mist,
                        modifier = Modifier
                            .background(if (on) Ink.mist else Color.Transparent, RoundedCornerShape(20.dp))
                            .border(1.dp, if (on) Ink.mist else Ink.mistLine, RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button) { intent = if (on) null else word }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (req.mode) {
                PauseMode.ENTRY -> {
                    PrimaryButton("Not now", act.notNow, Modifier.weight(1f), dark = true)
                    if (!waiting) QuietButton("Open $label", { act.goIn(intent) }, Modifier.weight(1f), dark = true)
                    else Spacer(Modifier.weight(1f))
                }
                PauseMode.SESSION_END -> {
                    PrimaryButton("Close $label", act.goHome, Modifier.weight(1f), dark = true)
                    val canGoOn = rule != null && (rule.opensPerDay == 0 || Store.opensToday(rule.id) < rule.opensPerDay)
                    if (canGoOn) QuietButton("Another session", { act.goIn(null) }, Modifier.weight(1f), dark = true)
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
                PauseMode.FREE ->
                    if (sitting && !sitOver) QuietButton("End early", act.close, Modifier.weight(1f), dark = true)
                    else PrimaryButton("Done", act.close, Modifier.weight(1f), dark = true)
                PauseMode.LET_GO -> PrimaryButton("Done", act.goHome, Modifier.weight(1f), dark = true)
            }
        }
    }
}

/** A soft tap on the wrist of the phone. [strength] is 1–255. */
private fun tap(context: Context, strength: Int) {
    val v = context.getSystemService(Vibrator::class.java) ?: return
    if (!v.hasVibrator()) return
    v.vibrate(VibrationEffect.createOneShot(35, strength.coerceIn(1, 255)))
}

private class Bell(private val context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val sound = pool.load(context, R.raw.bell, 1)

    fun play() {
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        pool.play(sound, 0.6f, 0.6f, 1, 0, 1f)
    }

    fun release() = pool.release()
}

/** The singing bowl for timed sits. Plays at media volume, so you set how loud it is. */
private class Bowl(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .build()
    private val sound = pool.load(context, R.raw.bowl, 1)
    @Volatile private var loaded = false
    private var waiting: Float? = null

    init {
        pool.setOnLoadCompleteListener { p, id, status ->
            if (id == sound && status == 0) {
                loaded = true
                waiting?.let { v -> p.play(sound, v, v, 1, 0, 1f) }
                waiting = null
            }
        }
    }

    fun play(volume: Float) {
        if (loaded) pool.play(sound, volume, volume, 1, 0, 1f) else waiting = volume
    }

    fun release() = pool.release()
}

@Composable
private fun rememberBowl(context: Context): Bowl {
    val bowl = remember { Bowl(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { bowl.release() } }
    return bowl
}

@Composable
private fun rememberBell(context: Context): Bell {
    val bell = remember { Bell(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { bell.release() } }
    return bell
}
