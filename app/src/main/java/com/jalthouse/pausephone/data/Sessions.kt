package com.jalthouse.pausephone.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * A stretch of time in which an app or site is open after a pause.
 * [end] and [nextBreather] are 0 when there is no session limit or no breather.
 */
class Session(
    val id: String,
    val start: Long,
    val end: Long,
    @Volatile var lastSeen: Long,
    @Volatile var nextBreather: Long,
) {
    /** The heads-up before the end has been given. */
    @Volatile var warned = false
}

/**
 * Open sessions, shared by the service and the pause screen. Kept on the phone too, so a
 * session survives Android restarting [pause] in the background.
 *
 * A session with a length (say 3 hours) lasts that long however often you leave and come
 * back: coming back inside it never counts as a new open. A session with no length ends
 * once you've been away from the app for [GRACE_MS].
 */
object Sessions {
    /** With no session length: leave for longer than this and the next visit is a new open. */
    const val GRACE_MS = 2 * 60_000L

    private val map = ConcurrentHashMap<String, Session>()
    @Volatile private var loaded = false

    private fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                val arr = JSONArray(Store.sessionsJson())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val s = Session(o.getString("id"), o.getLong("start"), o.getLong("end"), o.getLong("lastSeen"), o.getLong("nextBreather"))
                    s.warned = o.optBoolean("warned", false)
                    map[s.id] = s
                }
            }
            loaded = true
        }
    }

    /** Writes the open sessions down. Call after changing one. */
    fun save() {
        val arr = JSONArray()
        map.values.forEach { s ->
            arr.put(
                JSONObject().put("id", s.id).put("start", s.start).put("end", s.end)
                    .put("lastSeen", s.lastSeen).put("nextBreather", s.nextBreather).put("warned", s.warned),
            )
        }
        Store.saveSessionsJson(arr.toString())
    }

    fun start(rule: Rule, now: Long = System.currentTimeMillis()): Session {
        load()
        val s = Session(
            id = rule.id,
            start = now,
            end = if (rule.sessionMin > 0) now + rule.sessionMin * 60_000L else 0L,
            lastSeen = now,
            nextBreather = if (rule.breatherMin > 0) now + rule.breatherMin * 60_000L else 0L,
        )
        map[rule.id] = s
        save()
        return s
    }

    fun get(id: String): Session? {
        load()
        return map[id]
    }

    /** The session for [id] if it is still open. */
    fun active(id: String, now: Long = System.currentTimeMillis()): Session? {
        load()
        val s = map[id] ?: return null
        val expired = s.end in 1..now
        val wandered = s.end == 0L && now - s.lastSeen > GRACE_MS
        if (expired || wandered) {
            map.remove(id)
            save()
            return null
        }
        return s
    }

    fun end(id: String) {
        load()
        map.remove(id)
        save()
    }
}
