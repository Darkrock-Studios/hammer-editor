package com.darkrockstudios.apps.hammer.syncsessionmanager

import com.darkrockstudios.apps.hammer.utilities.RandomString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class SyncSessionManager<T : SynchronizationSession>(private val clock: Clock) {
	val lock = Mutex()
	private val syncIdGenerator = RandomString(30)
	private val synchronizationSessions = mutableMapOf<Long, T>()

	fun findSession(userId: Long): T? = synchronizationSessions[userId]
	fun terminateSession(userId: Long): Boolean = synchronizationSessions.remove(userId) != null

	suspend fun createNewSession(userId: Long, createSession: (userId: Long, syncId: String) -> T): String {
		lock.withLock {
			val newSyncId = syncIdGenerator.nextString()
			val newSession = createSession(userId, newSyncId)
			synchronizationSessions[userId] = newSession
			return newSyncId
		}
	}

	suspend fun hasActiveSyncSession(userId: Long): Boolean {
		lock.withLock {
			val session = synchronizationSessions[userId]
			return if (session == null || session.isExpired(clock)) {
				synchronizationSessions.remove(userId)
				false
			} else {
				true
			}
		}
	}

	suspend fun getActiveSyncSession(userId: Long): T? {
		lock.withLock {
			val session = synchronizationSessions[userId]
			return if (session == null || session.isExpired(clock)) {
				synchronizationSessions.remove(userId)
				null
			} else {
				session
			}
		}
	}

	suspend fun validateSyncId(userId: Long, syncId: String): Boolean {
		lock.withLock {
			val session = findSession(userId)
			return if (session?.syncId == syncId) {
				if (session.isExpired(clock).not()) {
					session.updateLastAccessed(clock)
					true
				} else {
					terminateSession(userId)
					false
				}
			} else {
				false
			}
		}
	}
}