package com.jalthouse.pausephone.data

import android.content.Context
import org.json.JSONObject

/**
 * Before and after: when Usage access is first allowed, [pause] notes the time per app
 * for the week before. Seven days later it compares that with the week since.
 * Only the phone's own numbers are used; nothing is estimated.
 */
object Progress {
    private const val WEEK = 7 * 86_400_000L

    /** Takes the "before" picture once, as soon as Usage access is on. Call from the background. */
    fun captureBaseline(context: Context) {
        if (Store.baselineAt != 0L || !Usage.granted(context)) return
        val s = Usage.lastDays(context, 7) ?: return
        if (s.totalMs <= 0) return
        val perApp = JSONObject()
        s.perApp.forEach { (pkg, ms) -> perApp.put(pkg, ms / s.days) }
        Store.saveBaseline(System.currentTimeMillis(), s.msPerDay, perApp.toString())
    }

    fun ready(): Boolean = Store.baselineAt != 0L && System.currentTimeMillis() - Store.baselineAt >= WEEK

    /** A few sentences about the first week (or any week since), or null if it's too early. */
    fun story(context: Context, rules: List<Rule>): String? {
        if (!ready()) return null
        val now = Usage.lastDays(context, 7) ?: return null
        val before = runCatching { JSONObject(Store.baselineApps) }.getOrNull() ?: return null
        val apps = rules.filter { it.kind == Kind.APP && before.has(it.id) }
        val lines = mutableListOf<String>()
        if (apps.isNotEmpty()) {
            val beforeMs = apps.sumOf { before.optLong(it.id) }
            val afterMs = apps.sumOf { r -> (now.perApp.find { it.first == r.id }?.second ?: 0L) / now.days }
            val names = apps.map { it.label }.let { if (it.size <= 2) it.joinToString(" and ") else it.take(2).joinToString(", ") + " and the others" }
            val saved = beforeMs - afterMs
            if (saved >= 60_000L) {
                lines += "You now spend about ${durationText(saved)} less a day in $names than in the week before [pause]."
                lines += "That's about ${durationText(saved * 7)} back this week, and around ${saved * 365 / 3_600_000L} hours a year at this pace."
            } else {
                lines += "Time in $names is about the same as before. That's fine: this isn't a score."
            }
        }
        val totalSaved = Store.baselineTotalPerDay - now.msPerDay
        if (totalSaved >= 5 * 60_000L) lines += "Across your whole phone, that's ${durationText(totalSaved)} a day less than before."
        val week = Store.week()
        val letGo = week.sumOf { it.second.letGo }
        if (letGo > 0) lines += "And ${times(letGo)} this week, you paused and chose not to go in."
        return lines.joinToString(" ").ifBlank { null }
    }
}
