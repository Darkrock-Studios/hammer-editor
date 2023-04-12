package com.darkrockstudios.apps.hammer.common.data.id

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.id.handler.IdHandler
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

abstract class IdRepository(private val projectDef: ProjectDef) : ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)
	private val projectSynchronizer: ClientProjectSynchronizer by projectInject()

	protected abstract val idHandlers: List<IdHandler>
	private val mutex = Mutex()

	private var nextId: Int = -1

	fun peekNextId(): Int = nextId

	suspend fun findNextId() {
		mutex.withLock {
			var lastId = -1

			idHandlers.forEach { handler ->
				val highestId = handler.findHighestId(projectDef)
				lastId = max(lastId, highestId)
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

	@OptIn(InternalCoroutinesApi::class)
	suspend fun claimNextId(): Int {
		mutex.withLock {
			val newSceneId = nextId
			recordNewId(newSceneId)
			nextId += 1
			return newSceneId
		}
	}
}