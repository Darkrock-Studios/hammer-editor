package com.darkrockstudios.apps.hammer.syncsessionmanager

import com.darkrockstudios.apps.hammer.utilities.RandomString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class SyncSessionManager<K, T : SynchronizationSession>(private val clock: Clock) {
	val lock = Mutex()
	private val syncIdGenerator = RandomString(30)
	private val synchronizationSessions = mutableMapOf<K, T>()

	fun findSession(key: K): T? = synchronizationSessions[key]
	fun terminateSession(key: K): Boolean = synchronizationSessions.remove(key) != null

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
			val session = findSession(key)
			return if (session?.syncId == syncId) {
				if (session.isExpired(clock).not() || allowExpired) {
					session.updateLastAccessed(clock)
					true
				} else {
					terminateSession(key)
					false
				}
			} else {
				false
			}
		}
	}
}