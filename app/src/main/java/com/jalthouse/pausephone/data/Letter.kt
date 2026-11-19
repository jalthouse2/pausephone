package com.jalthouse.pausephone.data

import java.time.format.TextStyle
import java.util.Locale

internal fun times(n: Int) = if (n == 1) "once" else if (n == 2) "twice" else "$n times"

/** A few calm sentences about the last seven days. No scores, no streaks. */
fun weekLetter(rules: List<Rule>): String {
    val week = Store.week()
    val pauses = week.sumOf { it.second.pauses }
    if (pauses == 0) return "Your week starts here. There's nothing to tell yet, and that's fine."
    val letGo = week.sumOf { it.second.letGo }
    val breathers = week.sumOf { it.second.breathers }
    val lines = mutableListOf<String>()
    lines += if (letGo > 0) "You paused ${times(pauses)} and let it go ${times(letGo)}."
    else "You paused ${times(pauses)}."
    val byApp = mutableMapOf<String, Int>()
    week.forEach { (_, d) -> d.letGoBy.forEach { (k, v) -> byApp[k] = (byApp[k] ?: 0) + v } }
    byApp.maxByOrNull { it.value }?.let { (id, n) ->
        val name = rules.find { it.id == id }?.label ?: id
        if (n > 1) lines += "You let $name go most often, ${times(n)}."
    }
    val active = week.filter { it.second.pauses > 0 }
    if (active.size >= 3) {
        val quiet = active.minByOrNull { it.second.wentIn }!!.first
        lines += "Your quietest day was ${quiet.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}."
    }
    if (breathers > 0) lines += "You took ${if (breathers == 1) "a breather" else "$breathers breathers"} along the way."
    return lines.joinToString(" ")
}

