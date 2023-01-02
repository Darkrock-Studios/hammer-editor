package com.darkrockstudios.apps.hammer.common.data.id.provider

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.provider.handler.IdHandler
import kotlin.math.max

abstract class IdProvider(private val projectDef: ProjectDef) {
	protected val idHandlers = mutableListOf<IdHandler>()

	private var nextId: Int = -1
	fun findNextId() {
		var lastId = -1

		idHandlers.forEach { handler ->
			val highestId = handler.findHighestId(projectDef)
			lastId = max(lastId, highestId)
		}

		nextId = if (lastId < 0) {
			0
		} else {
			lastId + 1
		}
	}

	fun claimNextSceneId(): Int {
		val newSceneId = nextId
		nextId += 1
		return newSceneId
	}
}