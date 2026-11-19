package com.jalthouse.pausephone.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.time.LocalDate
import java.time.ZoneId

/**
 * Your phone's own record of pickups and time in each app, read with your permission
 * ("Usage access"). Nothing is stored or sent anywhere: it is read when shown.
 */
object Usage {

    /** [days] of data ending now: pickups and foreground time per app. */
    data class Summary(
        val days: Int,
        val totalMs: Long,
        val pickups: Int,
        val perApp: List<Pair<String, Long>>, // package → time, most first
    ) {
        val pickupsPerDay: Int get() = if (days > 0) pickups / days else pickups
        val msPerDay: Long get() = if (days > 0) totalMs / days else totalMs
    }

    fun granted(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openSettings(context: Context) {
        val withPackage = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val plain = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(withPackage) }.recoverCatching { context.startActivity(plain) }
    }

    /** Today, from midnight. */
    fun today(context: Context): Summary? {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return between(context, start, System.currentTimeMillis(), 1)
    }

    /** The last [days] full days, ending at midnight this morning; falls back to the last 24 hours. */
    fun lastDays(context: Context, days: Int = 7): Summary? {
        val end = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val start = end - days * 86_400_000L
        val s = between(context, start, end, days)
        if (s != null && s.totalMs > 0) return s
        val now = System.currentTimeMillis()
        return between(context, now - 86_400_000L, now, 1)
    }

    private fun between(context: Context, from: Long, to: Long, days: Int): Summary? {
        if (!granted(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val skip = skipped(context)
        val events = runCatching { usm.queryEvents(from, to) }.getOrNull() ?: return null
        val open = HashMap<String, Long>()   // package → when it came to the front
        val time = HashMap<String, Long>()
        var pickups = 0
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> if (pkg !in skip) open.putIfAbsent(pkg, e.timeStamp)
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val began = open.remove(pkg) ?: continue
                    time[pkg] = (time[pkg] ?: 0L) + (e.timeStamp - began).coerceAtLeast(0L)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    // The screen went off: close everything that was open.
                    open.forEach { (p, began) -> time[p] = (time[p] ?: 0L) + (e.timeStamp - began).coerceAtLeast(0L) }
                    open.clear()
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> pickups++
            }
        }
        open.forEach { (p, began) -> time[p] = (time[p] ?: 0L) + (to - began).coerceAtLeast(0L) }
        val perApp = time.entries.filter { it.value >= 60_000L }.sortedByDescending { it.value }.map { it.key to it.value }
        return Summary(days, time.values.sum(), pickups, perApp)
    }

    /** The home screen, [pause] itself and the system UI don't count as "using an app". */
    private fun skipped(context: Context): Set<String> {
        val home = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
        ).map { it.activityInfo.packageName }
        return home.toSet() + setOf(context.packageName, "com.android.systemui", "android")
    }

    /** One sentence for the weekly letter, or null without permission or data. */
    fun weekSentence(context: Context): String? {
        val s = lastDays(context, 7) ?: return null
        if (s.totalMs <= 0) return null
        val top = s.perApp.firstOrNull()?.let { label(context, it.first) }
        return "Your phone counted about ${durationText(s.msPerDay)} a day on screen" +
            (if (top != null) ", most of it in $top." else ".")
    }

    fun label(context: Context, pkg: String): String =
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
}

/** "3 h 12 min", "45 min", "under a minute" */
fun durationText(ms: Long): String {
    val m = ms / 60_000L
    return when {
        m < 1 -> "under a minute"
        m < 60 -> "$m min"
        m % 60 == 0L -> "${m / 60} h"
        else -> "${m / 60} h ${m % 60} min"
    }
}
