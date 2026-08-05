package com.darkrockstudios.apps.hammer.syncsessionmanager

import com.darkrockstudios.apps.hammer.utilities.RandomString
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

class SyncSessionManager<K : Any, T : SynchronizationSession>(
	private val clock: Clock,
	secureRandom: SecureRandom,
) {
	private val syncIdGenerator = RandomString(30, secureRandom)
	private val synchronizationSessions = ConcurrentHashMap<K, T>()

	fun findSession(key: K): T? = synchronizationSessions[key]

	/** Number of currently non-expired sessions. Best-effort gauge for monitoring. */
	fun activeSessionCount(): Int = synchronizationSessions.count { (_, session) -> !session.isExpired(clock) }

	fun terminateSession(key: K): Boolean = synchronizationSessions.remove(key) != null

	/** Removes every session whose key matches [predicate]. */
	fun terminateSessions(predicate: (K) -> Boolean) {
		synchronizationSessions.keys.removeAll { predicate(it) }
	}

	suspend fun createNewSession(key: K, createSession: (key: K, syncId: String) -> T): String {
		val newSyncId = syncIdGenerator.nextString()
		val newSession = createSession(key, newSyncId)
		synchronizationSessions[key] = newSession
		return newSyncId
	}

	/**
	 * Atomically claim the session slot for [key]: replaces an expired session or one
	 * [canReplace] approves, otherwise leaves the holder in place and returns null.
	 * Check-then-create via [hasActiveSyncSession]/[createNewSession] races concurrent
	 * claimants; this does not.
	 */
	suspend fun claimSession(
		key: K,
		canReplace: (T) -> Boolean = { false },
		createSession: (key: K, syncId: String) -> T,
	): String? {
		val newSyncId = syncIdGenerator.nextString()
		var claimed = false
		synchronizationSessions.compute(key) { _, existing ->
			if (existing != null && !existing.isExpired(clock) && !canReplace(existing)) {
				existing
			} else {
				claimed = true
				createSession(key, newSyncId)
			}
		}
		return if (claimed) newSyncId else null
	}

	fun hasActiveSyncSession(key: K): Boolean {
		var isActive = false
		synchronizationSessions.computeIfPresent(key) { _, session ->
			if (session.isExpired(clock)) {
				null // remove expired session
			} else {
				isActive = true
				session // keep it
			}
		}
		return isActive
	}

	fun getActiveSyncSession(key: K): T? {
		var activeSession: T? = null
		synchronizationSessions.computeIfPresent(key) { _, session ->
			if (session.isExpired(clock)) {
				null // remove expired session
			} else {
				activeSession = session
				session // keep it
			}
		}
		return activeSession
	}

	fun validateSyncId(key: K, syncId: String, allowExpired: Boolean = false): Boolean {
		var isValid = false
		synchronizationSessions.computeIfPresent(key) { _, session ->
			if (session.syncId == syncId) {
				if (!session.isExpired(clock) || allowExpired) {
					session.updateLastAccessed(clock)
					isValid = true
					session // keep it
				} else {
					// expired and allowExpired is false
					isValid = false
					null // remove it
				}
			} else {
				// wrong syncId, keep session but return false
				isValid = false
				session
			}
		}
		return isValid
	}
}
