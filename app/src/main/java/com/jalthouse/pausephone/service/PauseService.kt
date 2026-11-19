package com.jalthouse.pausephone.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.jalthouse.pausephone.PauseActivity
import com.jalthouse.pausephone.PauseMode
import com.jalthouse.pausephone.data.Rule
import com.jalthouse.pausephone.data.Sessions
import com.jalthouse.pausephone.data.Store

/**
 * Watches which app (or, in supported browsers, which website) is in front and
 * shows the breathing pause before the ones that have a rule.
 */
class PauseService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentId: String? = null
    private var lastLaunch = 0L
    private var lastUrlCheck = 0L
    private var keyboards: Set<String> = emptySet()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> {
                    if (Store.unlockPauseSec > 0 && !Store.onBreak()) {
                        launch(PauseMode.UNLOCK, null)
                    }
                }
                Intent.ACTION_SCREEN_OFF -> currentId = null
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            checkSession()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        running = true
        val imm = getSystemService(InputMethodManager::class.java)
        keyboards = imm?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        handler.post(tick)
        // The Accessibility shortcut button, if you turn it on: one breath, anywhere.
        accessibilityButtonController.registerAccessibilityButtonCallback(
            object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) {
                    if (!PauseActivity.visible) launch(PauseMode.FREE, null)
                }
            },
        )
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tick)
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in IGNORED || pkg in keyboards) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastPackage = pkg
            lastSeenAt = System.currentTimeMillis()
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onForeground(pkg)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                if (pkg in BROWSERS) checkUrl(pkg, force = false)
        }
    }

    private fun onForeground(pkg: String) {
        if (pkg in BROWSERS) {
            checkUrl(pkg, force = true)
            return
        }
        val rule = Store.ruleForPackage(pkg)
        if (rule == null) {
            currentId = null
            return
        }
        enter(rule)
    }

    /** Reads the browser's address bar and applies a website rule if one matches. */
    private fun checkUrl(pkg: String, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUrlCheck < URL_CHECK_MS) return
        lastUrlCheck = now
        val appRule = Store.ruleForPackage(pkg)
        val root = rootInActiveWindow
        val bar = root?.findAccessibilityNodeInfosByViewId(BROWSERS.getValue(pkg))?.firstOrNull()
        // While you are typing in the address bar the text is not an address yet.
        val host = if (bar != null && !bar.isFocused) hostOf(bar.text?.toString()) else null
        val siteRule = host?.let { Store.ruleForHost(it) }
        val rule = siteRule ?: appRule
        if (rule == null) {
            if (host != null) currentId = null
            return
        }
        enter(rule)
    }

    private fun enter(rule: Rule) {
        val now = System.currentTimeMillis()
        if (PauseActivity.visible || now - lastLaunch < LAUNCH_GAP_MS) return
        lastDecision = decide(rule, now)
        if (Store.onBreak(now)) {
            currentId = null
            return
        }

        if (rule.blocked) {
            currentId = null
            launch(PauseMode.BLOCKED, rule, "blocked")
            return
        }
        val window = rule.blockingWindow()
        if (window != null) {
            currentId = null
            launch(PauseMode.BLOCKED, rule, "until:" + window.end)
            return
        }
        val session = Sessions.active(rule.id, now)
        if (session != null) {
            session.lastSeen = now
            currentId = rule.id
            return
        }
        if (rule.opensPerDay > 0 && Store.opensToday(rule.id) >= rule.opensPerDay) {
            currentId = null
            launch(PauseMode.BLOCKED, rule, "limit")
            return
        }
        currentId = rule.id
        if (rule.pauseSec == 0) {
            Store.recordOpen(rule.id)
            Sessions.start(rule, now)
        } else {
            launch(PauseMode.ENTRY, rule)
        }
    }

    private fun decide(rule: Rule, now: Long): String = when {
        Store.onBreak(now) -> "${rule.label}: no pause, you're on a break"
        rule.blocked -> "${rule.label}: blocked"
        rule.blockingWindow() != null -> "${rule.label}: blocked at this time"
        Sessions.active(rule.id, now) != null -> "${rule.label}: no pause, you're inside a session you already opened"
        rule.opensPerDay > 0 && Store.opensToday(rule.id) >= rule.opensPerDay -> "${rule.label}: no opens left today"
        rule.pauseSec == 0 -> "${rule.label}: no pause, its pause is set to none"
        else -> "${rule.label}: paused for ${rule.pauseSec} s"
    }

    /** Runs every few seconds: session end and breathers along the way. */
    private fun checkSession() {
        val id = currentId ?: return
        if (PauseActivity.visible) return
        val rule = Store.rule(id) ?: return
        val session = Sessions.get(id) ?: return
        val now = System.currentTimeMillis()
        session.lastSeen = now
        val headsUp = Store.headsUpMin * 60_000L
        if (headsUp > 0 && session.end > 0 && !session.warned && now >= session.end - headsUp && now < session.end) {
            session.warned = true
            HeadsUp.post(this, rule.label, Store.headsUpMin)
        }
        when {
            session.end in 1..now -> {
                Sessions.end(id)
                currentId = null
                launch(PauseMode.SESSION_END, rule)
            }
            session.nextBreather in 1..now -> {
                session.nextBreather = now + rule.breatherMin * 60_000L
                launch(PauseMode.BREATHER, rule)
            }
        }
    }

    private fun launch(mode: PauseMode, rule: Rule?, reason: String? = null) {
        lastLaunch = System.currentTimeMillis()
        startActivity(PauseActivity.launchIntent(this, mode, rule?.id, reason))
    }

    companion object {
        /** For the "Is it working?" check in Settings. */
        @Volatile var running = false
        @Volatile var lastPackage: String? = null
        @Volatile var lastSeenAt = 0L
        @Volatile var lastDecision: String? = null

        private const val TICK_MS = 5_000L
        private const val URL_CHECK_MS = 800L
        private const val LAUNCH_GAP_MS = 1_500L

        private val IGNORED = setOf("com.android.systemui", "android")

        /** Supported browsers and the view id of their address bar. */
        val BROWSERS = mapOf(
            "com.android.chrome" to "com.android.chrome:id/url_bar",
            "com.chrome.beta" to "com.chrome.beta:id/url_bar",
            "com.brave.browser" to "com.brave.browser:id/url_bar",
            "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
            "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.opera.browser" to "com.opera.browser:id/url_field",
            "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput",
        )

        /** "https://www.reddit.com/r/x" -> "reddit.com" */
        fun hostOf(text: String?): String? {
            val t = text?.trim()?.lowercase() ?: return null
            if (t.isEmpty() || t.contains(' ')) return null
            val noScheme = t.substringAfter("://")
            val host = noScheme.substringBefore('/').substringBefore('?').substringBefore(':')
            if (!host.contains('.')) return null
            return host.removePrefix("www.").removePrefix("m.")
        }
    }
}
