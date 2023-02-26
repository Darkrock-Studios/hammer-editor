package com.darkrockstudios.apps.hammer.common.data.id

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.handler.IdHandler
import kotlin.jvm.Synchronized
import kotlin.math.max

abstract class IdRepository(private val projectDef: ProjectDef) {
	protected abstract val idHandlers: List<IdHandler>

	private var nextId: Int = -1
	fun findNextId() {
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

	@Synchronized
	fun claimNextId(): Int {
		val newSceneId = nextId
		nextId += 1
		return newSceneId
	}
}