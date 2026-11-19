package com.jalthouse.pausephone.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** An app, a website, or a part of an app (like Reels or Shorts). */
enum class Kind { APP, SITE, PART }

/** A daily stretch of time, in minutes after midnight. It may run past midnight (22:00 to 07:00). */
data class TimeWindow(val startMin: Int, val endMin: Int) {
    fun contains(now: LocalTime): Boolean {
        val m = now.hour * 60 + now.minute
        return if (startMin <= endMin) m in startMin until endMin else m >= startMin || m < endMin
    }

    val start: String get() = hhmm(startMin)
    val end: String get() = hhmm(endMin)

    companion object {
        fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
    }
}

/**
 * One app or website and how [pause] treats it.
 * For apps, [id] is the package name; for websites it is the domain, e.g. "reddit.com".
 * A value of 0 means "off" or "no limit" for opens, session length and breather.
 */
data class Rule(
    val id: String,
    val label: String,
    val kind: Kind,
    val pauseSec: Int = 6,
    val breathSec: Int = 5,
    val opensPerDay: Int = 0,
    val sessionMin: Int = 0,
    val breatherMin: Int = 0,
    val blocked: Boolean = false,
    val blockTimes: List<TimeWindow> = emptyList(),
    /** Total minutes a day for each app or site; 0 = no limit. */
    val dailyMin: Int = 0,
    /** The group this app or site belongs to, whose settings it uses. */
    val group: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("label", label).put("kind", kind.name)
        .put("pauseSec", pauseSec).put("breathSec", breathSec)
        .put("opensPerDay", opensPerDay).put("sessionMin", sessionMin)
        .put("breatherMin", breatherMin).put("blocked", blocked).put("dailyMin", dailyMin)
        .put("blockTimes", JSONArray().also { a -> blockTimes.forEach { a.put(JSONArray().put(it.startMin).put(it.endMin)) } })

    /** The set time that is blocking right now, if any. */
    fun blockingWindow(now: LocalTime = LocalTime.now()): TimeWindow? = blockTimes.firstOrNull { it.contains(now) }

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
            blockTimes = o.optJSONArray("blockTimes")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    a.optJSONArray(i)?.let { w -> TimeWindow(w.optInt(0), w.optInt(1)) }
                }
            } ?: if (o.optBoolean("nightBlock", false)) listOf(TimeWindow(22 * 60, 7 * 60)) else emptyList(),
            dailyMin = o.optInt("dailyMin", 0),
        )
    }
}

/** An app or site in a group. */
data class Member(val id: String, val label: String, val kind: Kind)

/**
 * A named group of apps and sites that share one set of rules. Opens and sessions
 * still count per app or site.
 */
data class Group(
    val id: String,
    val name: String,
    val members: List<Member> = emptyList(),
    val pauseSec: Int = 6,
    val breathSec: Int = 5,
    val opensPerDay: Int = 0,
    val sessionMin: Int = 0,
    val breatherMin: Int = 0,
    val blocked: Boolean = false,
    val blockTimes: List<TimeWindow> = emptyList(),
    /** Total minutes a day for each app or site; 0 = no limit. */
    val dailyMin: Int = 0,
) {
    fun ruleFor(m: Member) = Rule(
        id = m.id, label = m.label, kind = m.kind,
        pauseSec = pauseSec, breathSec = breathSec, opensPerDay = opensPerDay,
        sessionMin = sessionMin, breatherMin = breatherMin, blocked = blocked,
        blockTimes = blockTimes, dailyMin = dailyMin, group = id,
    )

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name)
        .put("members", JSONArray().also { a ->
            members.forEach { a.put(JSONObject().put("id", it.id).put("label", it.label).put("kind", it.kind.name)) }
        })
        .put("pauseSec", pauseSec).put("breathSec", breathSec)
        .put("opensPerDay", opensPerDay).put("sessionMin", sessionMin)
        .put("breatherMin", breatherMin).put("blocked", blocked).put("dailyMin", dailyMin)
        .put("blockTimes", JSONArray().also { a -> blockTimes.forEach { a.put(JSONArray().put(it.startMin).put(it.endMin)) } })

    companion object {
        fun fromJson(o: JSONObject): Group {
            val ms = o.optJSONArray("members")
            val members = (0 until (ms?.length() ?: 0)).mapNotNull { i ->
                ms?.optJSONObject(i)?.let { m ->
                    Member(
                        m.getString("id"),
                        m.optString("label", m.getString("id")),
                        runCatching { Kind.valueOf(m.optString("kind", "APP")) }.getOrDefault(Kind.APP),
                    )
                }
            }
            val r = Rule.fromJson(JSONObject(o.toString()).put("id", o.getString("id")).put("label", o.optString("name")))
            return Group(
                id = o.getString("id"), name = o.optString("name", "Group"), members = members,
                pauseSec = r.pauseSec, breathSec = r.breathSec, opensPerDay = r.opensPerDay,
                sessionMin = r.sessionMin, breatherMin = r.breatherMin, blocked = r.blocked, blockTimes = r.blockTimes,
                dailyMin = r.dailyMin,
            )
        }

        /** An old single-app rule becomes a group of one, named after it. */
        fun fromRule(r: Rule) = Group(
            id = "g_" + r.id, name = r.label, members = listOf(Member(r.id, r.label, r.kind)),
            pauseSec = r.pauseSec, breathSec = r.breathSec, opensPerDay = r.opensPerDay,
            sessionMin = r.sessionMin, breatherMin = r.breatherMin, blocked = r.blocked, blockTimes = r.blockTimes,
        )
    }
}

data class DayStats(
    val pauses: Int = 0,
    val wentIn: Int = 0,
    val letGo: Int = 0,
    val breathers: Int = 0,
    val opens: Map<String, Int> = emptyMap(),
    /** How many times each app or site was let go. */
    val letGoBy: Map<String, Int> = emptyMap(),
    /** What you said you went in for, and how often. */
    val intents: Map<String, Int> = emptyMap(),
)

/** Everything the app remembers, kept on the phone in SharedPreferences. */
object Store {
    private lateinit var prefs: SharedPreferences

    /** Bumped on every change so Compose screens redraw. */
    var version by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences("pausephone", Context.MODE_PRIVATE)
        }
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Tells the screens to redraw. Always on the main thread, wherever the change came from. */
    private fun changed() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) version++ else main.post { version++ }
    }

    // ---- groups and rules ----

    fun groups(): List<Group> {
        val raw = prefs.getString("groups", null)
        if (raw == null) {
            // Earlier versions kept one rule per app; each becomes a group of one.
            val old = oldRules()
            if (old.isEmpty()) return emptyList()
            val migrated = old.map { Group.fromRule(it) }
            writeGroups(migrated)
            return migrated
        }
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> runCatching { Group.fromJson(arr.getJSONObject(i)) }.getOrNull() }
    }

    private fun oldRules(): List<Rule> {
        val raw = prefs.getString("rules", null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> runCatching { Rule.fromJson(arr.getJSONObject(i)) }.getOrNull() }
    }

    private fun writeGroups(list: List<Group>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("groups", arr.toString()).apply()
        changed()
    }

    fun group(id: String): Group? = groups().find { it.id == id }

    fun saveGroup(g: Group) {
        val list = groups().toMutableList()
        val i = list.indexOfFirst { it.id == g.id }
        if (i >= 0) list[i] = g else list.add(g)
        writeGroups(list)
    }

    fun removeGroup(id: String) = writeGroups(groups().filterNot { it.id == id })

    /** A new, empty group with your usual pause. */
    fun newGroup(name: String): Group {
        val g = Group(id = "g" + System.currentTimeMillis(), name = name.ifBlank { "My group" }, pauseSec = defaultPause)
        saveGroup(g)
        return g
    }

    /** Sets a group's members. An app or site lives in one group, so it leaves any other. */
    fun setMembers(groupId: String, members: List<Member>) {
        val ids = members.map { it.id }.toSet()
        writeGroups(
            groups().map { g ->
                if (g.id == groupId) g.copy(members = members.distinctBy { it.id })
                else g.copy(members = g.members.filterNot { it.id in ids })
            },
        )
    }

    fun groupOf(memberId: String): Group? = groups().find { g -> g.members.any { it.id == memberId } }

    /** Every app and site, each with its group's settings. */
    fun rules(): List<Rule> = groups().flatMap { g -> g.members.map { g.ruleFor(it) } }

    fun rule(id: String): Rule? = rules().find { it.id == id }

    fun ruleForPackage(pkg: String): Rule? = rules().find { it.kind == Kind.APP && it.id == pkg }

    fun ruleForPart(id: String): Rule? = rules().find { it.kind == Kind.PART && it.id == id }

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

    /** "light", "dark" or "system". */
    var theme: String
        get() = prefs.getString("theme", "light") ?: "light"
        set(v) { prefs.edit().putString("theme", v).apply(); changed() }

    /** Dark dots on soft white for the breathing screen (the default), instead of white on black. */
    var breatheLight: Boolean
        get() = prefs.getBoolean("breatheLight", true)
        set(v) { prefs.edit().putBoolean("breatheLight", v).apply(); changed() }

    /** While now is before this time, [pause] steps aside completely. 0 = not on a break. */
    var breakUntil: Long
        get() = prefs.getLong("breakUntil", 0L)
        set(v) { prefs.edit().putLong("breakUntil", v).apply(); changed() }

    fun onBreak(now: Long = System.currentTimeMillis()) = breakUntil > now

    // ---- lists you can edit ----

    private fun list(key: String, default: List<String>): List<String> {
        val raw = prefs.getString(key, null) ?: return default
        val a = runCatching { JSONArray(raw) }.getOrNull() ?: return default
        return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
    }

    private fun putList(key: String, v: List<String>) {
        prefs.edit().putString(key, JSONArray(v).toString()).apply()
        changed()
    }

    /** Suggestions to choose from; anything removed from a list can be added back from here. */
    val INTENTION_IDEAS = listOf(
        "reply to someone", "look something up", "just browsing", "check messages", "post something",
        "watch one video", "learn something", "rest and unwind", "check the news", "find a recipe",
    )
    val INSTEAD_IDEAS = listOf(
        "stretch for a minute", "step outside", "drink a glass of water", "look out the window",
        "take three slow breaths", "call someone you love", "read a few pages", "tidy one small thing",
        "make a cup of tea", "write down what's on your mind", "walk around the block", "put on a song you love",
        "sit with your eyes closed", "water a plant", "text a friend just to say hi", "look at something far away",
        "notice five things you can see", "do ten slow squats",
    )
    val SIT_INTENTIONS = listOf("calm", "focus", "gratitude", "kindness", "letting go", "rest", "courage", "just be")

    /** Today's intention, set in the morning; null when none for today. */
    fun todayIntention(): String? {
        val day = prefs.getString("dayIntentionDate", null) ?: return null
        return if (day == logicalToday().toString()) prefs.getString("dayIntention", null)?.takeIf { it.isNotBlank() } else null
    }

    fun setTodayIntention(text: String) {
        prefs.edit().putString("dayIntention", text.trim()).putString("dayIntentionDate", logicalToday().toString()).apply()
        changed()
    }

    /** A morning notification asking for an intention for the day. */
    var intentionReminder: Boolean
        get() = prefs.getBoolean("intentionReminder", true)
        set(v) { prefs.edit().putBoolean("intentionReminder", v).apply(); changed() }

    /** When it comes, in minutes after midnight. */
    var intentionTime: Int
        get() = prefs.getInt("intentionTime", 8 * 60 + 30)
        set(v) { prefs.edit().putInt("intentionTime", v).apply(); changed() }

    val DAY_INTENTIONS = listOf("calm", "focus", "gratitude", "kindness", "patience", "presence", "rest", "play")

    /** An intention to hold while you sit; empty = none. */
    var sitIntention: String
        get() = prefs.getString("sitIntention", "") ?: ""
        set(v) { prefs.edit().putString("sitIntention", v).apply(); changed() }

    /** Show the "going in to…" words at the pause. Off by default, to keep the pause quiet. */
    var askIntent: Boolean
        get() = prefs.getBoolean("askIntent", false)
        set(v) { prefs.edit().putBoolean("askIntent", v).apply(); changed() }

    /** Words you can tap at the pause to say what you're going in for. */
    var intentions: List<String>
        get() = list("intentions", listOf("reply to someone", "look something up", "just browsing"))
        set(v) = putList("intentions", v)

    /** Things to do instead, offered after "Never mind". */
    var ideas: List<String>
        get() = list("ideas", listOf("stretch for a minute", "step outside", "drink a glass of water", "look out the window"))
        set(v) = putList("ideas", v)

    // ---- from the introduction ----

    /** How present you said you feel on your phone: "always", "sometimes" or "rarely". */
    var presence: String
        get() = prefs.getString("presence", "sometimes") ?: "sometimes"
        set(v) { prefs.edit().putString("presence", v).apply(); changed() }

    /** How many times a day you guessed you pick up your phone. */
    var pickupsGuess: Int
        get() = prefs.getInt("pickupsGuess", 60)
        set(v) { prefs.edit().putInt("pickupsGuess", v).apply(); changed() }

    /** Time on the phone per day, from Usage access during the introduction. 0 = not known. */
    var usageMsPerDay: Long
        get() = prefs.getLong("usageMsPerDay", 0L)
        set(v) { prefs.edit().putLong("usageMsPerDay", v).apply(); changed() }

    /** How many minutes a day you guessed you spend on your phone, in the introduction (0 = no guess). */
    var timeGuessMin: Int
        get() = prefs.getInt("timeGuessMin", 0)
        set(v) { prefs.edit().putInt("timeGuessMin", v).apply(); changed() }

    /** True when [pickupsGuess] came from the phone's own count rather than a guess. */
    var pickupsReal: Boolean
        get() = prefs.getBoolean("pickupsReal", false)
        set(v) { prefs.edit().putBoolean("pickupsReal", v).apply(); changed() }

    /** When the "before" picture was taken, and what it showed (time per day, overall and per app). */
    val baselineAt: Long get() = prefs.getLong("baselineAt", 0L)
    val baselineTotalPerDay: Long get() = prefs.getLong("baselineTotal", 0L)
    val baselineApps: String get() = prefs.getString("baselineApps", "{}") ?: "{}"

    fun saveBaseline(at: Long, totalPerDay: Long, appsJson: String) {
        prefs.edit().putLong("baselineAt", at).putLong("baselineTotal", totalPerDay).putString("baselineApps", appsJson).apply()
        changed()
    }

    /** The pause new apps and sites start with: longer if you said you're rarely present. */
    val defaultPause: Int
        get() = when (presence) {
            "always" -> 4
            "rarely" -> 8
            else -> 6
        }

    /** A short letter about your week, as a notification on Sunday mornings. */
    var letterOn: Boolean
        get() = prefs.getBoolean("letterOn", true)
        set(v) { prefs.edit().putBoolean("letterOn", v).apply(); changed() }

    // ---- open sessions (no redraw needed) ----

    fun sessionsJson(): String = prefs.getString("sessions", "[]") ?: "[]"
    fun saveSessionsJson(json: String) { prefs.edit().putString("sessions", json).apply() }

    // ---- your day ----

    /** The hour your day starts, for opens and counts: 0 = midnight, up to 6 for night owls. */
    var dayStartHour: Int
        get() = prefs.getInt("dayStartHour", 0)
        set(v) { prefs.edit().putInt("dayStartHour", v).apply(); changed() }

    /** Today, as [pause] counts it: before [dayStartHour] it is still yesterday. */
    fun logicalToday(): LocalDate = LocalDateTime.now().minusHours(dayStartHour.toLong()).toLocalDate()

    /** When the next day starts, in epoch millis. */
    fun nextDayStart(): Long = logicalToday().plusDays(1).atTime(dayStartHour, 0)
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    // ---- protect your choices ----

    /** Seconds added to a pause for every open of that app or site today. 0 = off. */
    var growSec: Int
        get() = prefs.getInt("growSec", 0)
        set(v) { prefs.edit().putInt("growSec", v).apply(); changed() }

    /** Minutes before a session ends to give a quiet heads-up. 0 = none. */
    var headsUpMin: Int
        get() = prefs.getInt("headsUpMin", 1)
        set(v) { prefs.edit().putInt("headsUpMin", v).apply(); changed() }

    /** Seconds of breathing before you can loosen a rule or take a break. 0 = off. */
    var guardSec: Int
        get() {
            // Earlier versions allowed long waits; bring them down to the new 5 s default once.
            if (!prefs.getBoolean("guardEased", false)) {
                val v = prefs.getInt("guardSec", 5)
                prefs.edit().putInt("guardSec", if (v > 5) 5 else v).putBoolean("guardEased", true).apply()
            }
            return prefs.getInt("guardSec", 5)
        }
        set(v) { prefs.edit().putInt("guardSec", v).apply(); changed() }

    // ---- sitting (the meditation timer) ----

    var sitMin: Int
        get() = prefs.getInt("sitMin", 5)
        set(v) { prefs.edit().putInt("sitMin", v).apply(); changed() }

    var sitBreath: Int
        get() = prefs.getInt("sitBreath", 5)
        set(v) { prefs.edit().putInt("sitBreath", v).apply(); changed() }

    /** A soft bowl as the sit begins; the end always has one. */
    var sitStartBowl: Boolean
        get() = prefs.getBoolean("sitStartBowl", true)
        set(v) { prefs.edit().putBoolean("sitStartBowl", v).apply(); changed() }

    // ---- breathing ----

    /** A soft tap at the start of each breath in and out. */
    var breathVibration: Boolean
        get() = prefs.getBoolean("breathVibration", true)
        set(v) { prefs.edit().putBoolean("breathVibration", v).apply(); changed() }

    /** A quiet bell as the dots come to rest. Silent when the phone is on silent or vibrate. */
    var restBell: Boolean
        get() = prefs.getBoolean("restBell", false)
        set(v) { prefs.edit().putBoolean("restBell", v).apply(); changed() }

    // ---- daily stats ----

    private fun dayKey(date: LocalDate = logicalToday()) = "stats_$date"

    private fun dayJson(date: LocalDate = logicalToday()): JSONObject =
        runCatching { JSONObject(prefs.getString(dayKey(date), "{}")!!) }.getOrDefault(JSONObject())

    private fun saveDay(o: JSONObject) {
        prefs.edit().putString(dayKey(), o.toString()).apply()
        changed()
    }

    private fun counts(o: JSONObject?): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        o?.keys()?.forEach { k -> map[k] = o.optInt(k, 0) }
        return map
    }

    private fun addCount(o: JSONObject, field: String, key: String) {
        val m = o.optJSONObject(field) ?: JSONObject()
        m.put(key, m.optInt(key, 0) + 1)
        o.put(field, m)
    }

    fun day(date: LocalDate): DayStats {
        val o = dayJson(date)
        return DayStats(
            pauses = o.optInt("pauses", 0),
            wentIn = o.optInt("wentIn", 0),
            letGo = o.optInt("letGo", 0),
            breathers = o.optInt("breathers", 0),
            opens = counts(o.optJSONObject("opens")),
            letGoBy = counts(o.optJSONObject("letGoBy")),
            intents = counts(o.optJSONObject("intents")),
        )
    }

    fun today(): DayStats = day(logicalToday())

    /** The last seven days, oldest first, ending today. */
    fun week(): List<Pair<LocalDate, DayStats>> {
        val today = logicalToday()
        return (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to day(it) }
    }

    fun bump(field: String) {
        val o = dayJson()
        o.put(field, o.optInt(field, 0) + 1)
        saveDay(o)
    }

    fun opensToday(id: String): Int = today().opens[id] ?: 0

    /** Time spent today in [id] while [pause] watched it, in ms (counts toward a daily limit). */
    fun timeToday(id: String): Long = dayJson().optJSONObject("time")?.optLong(id, 0L) ?: 0L

    /** Adds time in [id] to today's total. Called every few seconds by the service. */
    fun addTime(id: String, ms: Long) {
        val o = dayJson()
        val t = o.optJSONObject("time") ?: JSONObject()
        t.put(id, t.optLong(id, 0L) + ms)
        o.put("time", t)
        prefs.edit().putString(dayKey(), o.toString()).apply()
    }

    /** Counts one open of [id] and one "went in", with what you went in for if you said. */
    fun recordOpen(id: String, intent: String? = null) {
        val o = dayJson()
        addCount(o, "opens", id)
        o.put("wentIn", o.optInt("wentIn", 0) + 1)
        if (!intent.isNullOrBlank()) addCount(o, "intents", intent)
        saveDay(o)
    }

    /** Counts one "let it go", for [id] if it was an app or site. */
    fun recordLetGo(id: String?) {
        val o = dayJson()
        o.put("letGo", o.optInt("letGo", 0) + 1)
        if (id != null) addCount(o, "letGoBy", id)
        saveDay(o)
    }
}
