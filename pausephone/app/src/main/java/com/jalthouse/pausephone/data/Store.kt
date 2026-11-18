package com.jalthouse.pausephone.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

enum class Kind { APP, SITE }

/**
 * One app or website and how [pause]phone treats it.
 * For apps, [id] is the package name; for websites it is the domain, e.g. "reddit.com".
 * A value of 0 means "off" or "no limit" for opens, session length and breather.
 */
data class Rule(
    val id: String,
    val label: String,
    val kind: Kind,
    val pauseSec: Int = 6,
    val breathSec: Int = 4,
    val opensPerDay: Int = 0,
    val sessionMin: Int = 0,
    val breatherMin: Int = 0,
    val blocked: Boolean = false,
    val nightBlock: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("label", label).put("kind", kind.name)
        .put("pauseSec", pauseSec).put("breathSec", breathSec)
        .put("opensPerDay", opensPerDay).put("sessionMin", sessionMin)
        .put("breatherMin", breatherMin).put("blocked", blocked).put("nightBlock", nightBlock)

    companion object {
        fun fromJson(o: JSONObject) = Rule(
            id = o.getString("id"),
            label = o.optString("label", o.getString("id")),
            kind = runCatching { Kind.valueOf(o.optString("kind", "APP")) }.getOrDefault(Kind.APP),
            pauseSec = o.optInt("pauseSec", 6),
            breathSec = o.optInt("breathSec", 4),
            opensPerDay = o.optInt("opensPerDay", 0),
            sessionMin = o.optInt("sessionMin", 0),
            breatherMin = o.optInt("breatherMin", 0),
            blocked = o.optBoolean("blocked", false),
            nightBlock = o.optBoolean("nightBlock", false),
        )
    }
}

data class DayStats(
    val pauses: Int = 0,
    val wentIn: Int = 0,
    val letGo: Int = 0,
    val breathers: Int = 0,
    val opens: Map<String, Int> = emptyMap(),
)

/** Everything the app remembers, kept on the phone in SharedPreferences. */
object Store {
    private lateinit var prefs: SharedPreferences

    /** Bumped on every change so Compose screens redraw. */
    var version by mutableIntStateOf(0)
        private set

    /** Night blocking runs from 22:00 to 07:00. */
    val nightStart: LocalTime = LocalTime.of(22, 0)
    val nightEnd: LocalTime = LocalTime.of(7, 0)

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences("pausephone", Context.MODE_PRIVATE)
        }
    }

    private fun changed() {
        version++
    }

    // ---- rules ----

    fun rules(): List<Rule> {
        val raw = prefs.getString("rules", null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { Rule.fromJson(arr.getJSONObject(i)) }.getOrNull()
        }
    }

    private fun writeRules(list: List<Rule>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("rules", arr.toString()).apply()
        changed()
    }

    fun rule(id: String): Rule? = rules().find { it.id == id }

    fun saveRule(rule: Rule) {
        val list = rules().toMutableList()
        val i = list.indexOfFirst { it.id == rule.id }
        if (i >= 0) list[i] = rule else list.add(rule)
        writeRules(list)
    }

    fun removeRule(id: String) = writeRules(rules().filterNot { it.id == id })

    fun ruleForPackage(pkg: String): Rule? = rules().find { it.kind == Kind.APP && it.id == pkg }

    fun ruleForHost(host: String): Rule? = rules().find {
        it.kind == Kind.SITE && (host == it.id || host.endsWith("." + it.id))
    }

    // ---- settings ----

    var welcomeDone: Boolean
        get() = prefs.getBoolean("welcomeDone", false)
        set(v) { prefs.edit().putBoolean("welcomeDone", v).apply(); changed() }

    /** Seconds of breathing each time the phone is unlocked. 0 = off. */
    var unlockPauseSec: Int
        get() = prefs.getInt("unlockPauseSec", 0)
        set(v) { prefs.edit().putInt("unlockPauseSec", v).apply(); changed() }

    var unlockBreathSec: Int
        get() = prefs.getInt("unlockBreathSec", 4)
        set(v) { prefs.edit().putInt("unlockBreathSec", v).apply(); changed() }

    fun isNight(now: LocalTime = LocalTime.now()): Boolean =
        if (nightStart <= nightEnd) now >= nightStart && now < nightEnd
        else now >= nightStart || now < nightEnd

    // ---- daily stats ----

    private fun dayKey() = "stats_" + LocalDate.now().toString()

    private fun dayJson(): JSONObject =
        runCatching { JSONObject(prefs.getString(dayKey(), "{}")!!) }.getOrDefault(JSONObject())

    fun today(): DayStats {
        val o = dayJson()
        val opens = o.optJSONObject("opens")
        val map = mutableMapOf<String, Int>()
        opens?.keys()?.forEach { k -> map[k] = opens.optInt(k, 0) }
        return DayStats(
            pauses = o.optInt("pauses", 0),
            wentIn = o.optInt("wentIn", 0),
            letGo = o.optInt("letGo", 0),
            breathers = o.optInt("breathers", 0),
            opens = map,
        )
    }

    fun bump(field: String) {
        val o = dayJson()
        o.put(field, o.optInt(field, 0) + 1)
        prefs.edit().putString(dayKey(), o.toString()).apply()
        changed()
    }

    fun opensToday(id: String): Int = today().opens[id] ?: 0

    /** Counts one open of [id] and one "went in". */
    fun recordOpen(id: String) {
        val o = dayJson()
        val opens = o.optJSONObject("opens") ?: JSONObject()
        opens.put(id, opens.optInt(id, 0) + 1)
        o.put("opens", opens)
        o.put("wentIn", o.optInt("wentIn", 0) + 1)
        prefs.edit().putString(dayKey(), o.toString()).apply()
        changed()
    }
}
