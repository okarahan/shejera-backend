package com.shejera.importing

/**
 * Single global in-memory import session.
 * Later this can be keyed by user/session.
 */
object ImportSessionStore {
    @Volatile
    var current: ImportSession? = null
        private set

    @Synchronized
    fun set(session: ImportSession) {
        current = session
    }

    @Synchronized
    fun update(transform: (ImportSession) -> ImportSession) {
        val existing = current ?: return
        current = transform(existing)
    }

    @Synchronized
    fun clear() {
        current = null
    }
}
