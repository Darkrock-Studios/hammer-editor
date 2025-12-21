package com.darkrockstudios.apps.hammer.syncsessionmanager

import com.darkrockstudios.apps.hammer.utilities.RandomString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import kotlin.time.Clock

class SyncSessionManager<K, T : SynchronizationSession>(
	private val clock: Clock,
	secureRandom: SecureRandom,
) {
	private val lock = Mutex()
	private val syncIdGenerator = RandomString(30, secureRandom)
	private val synchronizationSessions = mutableMapOf<K, T>()

	private fun findSessionInternal(key: K): T? = synchronizationSessions[key]
	private fun terminateSessionInternal(key: K): Boolean = synchronizationSessions.remove(key) != null

	suspend fun findSession(key: K): T? {
		lock.withLock {
			return findSessionInternal(key)
		}
	}

	suspend fun terminateSession(key: K): Boolean {
		lock.withLock {
			return terminateSessionInternal(key)
		}
	}

	suspend fun createNewSession(key: K, createSession: (key: K, syncId: String) -> T): String {
		lock.withLock {
			val newSyncId = syncIdGenerator.nextString()
			val newSession = createSession(key, newSyncId)
			synchronizationSessions[key] = newSession
			return newSyncId
		}
	}

	suspend fun hasActiveSyncSession(key: K): Boolean {
		lock.withLock {
			val session = synchronizationSessions[key]
			return if (session == null || session.isExpired(clock)) {
				synchronizationSessions.remove(key)
				false
			} else {
				true
			}
		}
	}

	suspend fun getActiveSyncSession(key: K): T? {
		lock.withLock {
			val session = synchronizationSessions[key]
			return if (session == null || session.isExpired(clock)) {
				synchronizationSessions.remove(key)
				null
			} else {
				session
			}
		}
	}

	suspend fun validateSyncId(key: K, syncId: String, allowExpired: Boolean): Boolean {
		lock.withLock {
			val session = findSessionInternal(key)
			return if (session?.syncId == syncId) {
				if (session.isExpired(clock).not() || allowExpired) {
					session.updateLastAccessed(clock)
					true
				} else {
					terminateSessionInternal(key)
					false
				}
			} else {
				false
			}
		}
	}
}