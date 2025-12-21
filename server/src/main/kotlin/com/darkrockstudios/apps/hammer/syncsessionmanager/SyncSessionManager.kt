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

	fun terminateSession(key: K): Boolean = synchronizationSessions.remove(key) != null

	suspend fun createNewSession(key: K, createSession: (key: K, syncId: String) -> T): String {
		val newSyncId = syncIdGenerator.nextString()
		val newSession = createSession(key, newSyncId)
		synchronizationSessions[key] = newSession
		return newSyncId
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
