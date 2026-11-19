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

    private fun between(context: Context, from: Long, to: Long, days: Int): Summary? =
        scan(context, from, to, days, to - from)?.summary

    /** A closer look: time and pickups in buckets (hours of a day, or days of a week). */
    class Detail(
        val summary: Summary,
        val from: Long,
        val bucketMs: Long,
        val time: LongArray,
        val pickups: IntArray,
        val opens: Map<String, Int>,
        val firstPickup: Long?,
    )

    /** Today, hour by hour. */
    fun todayDetail(context: Context): Detail? {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return scan(context, start, System.currentTimeMillis(), 1, 3_600_000L, buckets = 24)
    }

    /** The last seven days including today, day by day. */
    fun weekDetail(context: Context): Detail? {
        val start = LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return scan(context, start, System.currentTimeMillis(), 7, 86_400_000L, buckets = 7)
    }

    private fun scan(
        context: Context,
        from: Long,
        to: Long,
        days: Int,
        bucketMs: Long,
        buckets: Int = 1,
    ): Detail? {
        if (!granted(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val skip = skipped(context)
        val events = runCatching { usm.queryEvents(from, to) }.getOrNull() ?: return null
        val n = buckets.coerceAtLeast(1)
        val slot = LongArray(n)
        val pick = IntArray(n)
        val open = HashMap<String, Long>()
        val time = HashMap<String, Long>()
        val opens = HashMap<String, Int>()
        var pickups = 0
        var front: String? = null
        var first: Long? = null
        fun bucket(t: Long) = ((t - from) / bucketMs).toInt().coerceIn(0, n - 1)
        fun close(pkg: String, began: Long, end: Long) {
            if (end <= began) return
            time[pkg] = (time[pkg] ?: 0L) + (end - began)
            var t = began
            while (t < end) {
                val b = bucket(t)
                val edge = (from + (b + 1) * bucketMs).coerceAtMost(end)
                slot[b] += (if (edge > t) edge else end) - t
                t = if (edge > t) edge else end
            }
        }
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> if (pkg !in skip) {
                    if (open.putIfAbsent(pkg, e.timeStamp) == null && front != pkg) opens[pkg] = (opens[pkg] ?: 0) + 1
                    front = pkg
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val began = open.remove(pkg) ?: continue
                    close(pkg, began, e.timeStamp)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    open.forEach { (p, began) -> close(p, began, e.timeStamp) }
                    open.clear()
                    front = null
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> {
                    pickups++
                    pick[bucket(e.timeStamp)]++
                    if (first == null) first = e.timeStamp
                    front = null
                }
            }
        }
        open.forEach { (p, began) -> close(p, began, to) }
        val perApp = time.entries.filter { it.value >= 60_000L }.sortedByDescending { it.value }.map { it.key to it.value }
        return Detail(Summary(days, time.values.sum(), pickups, perApp), from, bucketMs, slot, pick, opens, first)
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
