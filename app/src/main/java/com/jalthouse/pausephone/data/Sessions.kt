package com.jalthouse.pausephone.data

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

/** Live sessions, shared by the service and the pause screen (same process). */
object Sessions {
    /** Leave an app for longer than this and the next visit counts as a new open. */
    const val GRACE_MS = 2 * 60_000L

    private val map = ConcurrentHashMap<String, Session>()

    fun start(rule: Rule, now: Long = System.currentTimeMillis()): Session {
        val s = Session(
            id = rule.id,
            start = now,
            end = if (rule.sessionMin > 0) now + rule.sessionMin * 60_000L else 0L,
            lastSeen = now,
            nextBreather = if (rule.breatherMin > 0) now + rule.breatherMin * 60_000L else 0L,
        )
        map[rule.id] = s
        return s
    }

    fun get(id: String): Session? = map[id]

    /** The session for [id] if it is still running and was seen recently. */
    fun active(id: String, now: Long = System.currentTimeMillis()): Session? {
        val s = map[id] ?: return null
        val expired = s.end in 1..now
        val wandered = now - s.lastSeen > GRACE_MS
        if (expired || wandered) {
            map.remove(id)
            return null
        }
        return s
    }

    fun end(id: String) {
        map.remove(id)
    }
}
