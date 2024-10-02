package com.darkrockstudios.apps.hammer.common.data.id

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.id.handler.IdHandler
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.math.max

abstract class IdRepository(private val projectDef: ProjectDef) : ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)
	private val projectSynchronizer: ClientProjectSynchronizer by projectInject()

	protected abstract val idHandlers: List<IdHandler>
	private val mutex = reentrantLock()

	private var nextId: Int = -1

	fun peekNextId(): Int = nextId
	fun peekLastId(): Int = nextId - 1

	suspend fun findNextId() {
		mutex.withLock {
			var lastId = -1

			idHandlers.forEach { handler ->
				val highestId = handler.findHighestId(projectDef)
				lastId = max(lastId, highestId)
			}

			if (projectSynchronizer.isServerSynchronized()) {
				projectSynchronizer.deletedIds().maxOrNull()?.let { maxDeletedId ->
					lastId = max(lastId, maxDeletedId)
				}
			}

			nextId = if (lastId < 0) {
				1
			} else {
				lastId + 1
			}
		}
	}

	private suspend fun recordNewId(claimedId: Int) {
		if (projectSynchronizer.isServerSynchronized()) {
			projectSynchronizer.recordNewId(claimedId)
		}
	}

	suspend fun claimNextId(): Int {
		return mutex.withLock {
			val newSceneId = nextId
			recordNewId(newSceneId)
			nextId += 1
			newSceneId
		}
	}

	companion object {
		const val FIRST_ID = 1
	}
}