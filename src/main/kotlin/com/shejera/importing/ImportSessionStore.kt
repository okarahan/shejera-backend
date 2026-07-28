package com.shejera.importing

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory import sessions keyed by user id.
 */
object ImportSessionStore {
    private val sessions = ConcurrentHashMap<UUID, ImportSession>()

    fun get(userId: UUID): ImportSession? = sessions[userId]

    fun set(
        userId: UUID,
        session: ImportSession,
    ) {
        sessions[userId] = session
    }

    fun update(
        userId: UUID,
        transform: (ImportSession) -> ImportSession,
    ) {
        sessions.computeIfPresent(userId) { _, current -> transform(current) }
    }

    fun clear(userId: UUID) {
        sessions.remove(userId)
    }
}
