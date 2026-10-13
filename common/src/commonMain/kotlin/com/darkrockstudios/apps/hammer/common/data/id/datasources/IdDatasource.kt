package com.darkrockstudios.apps.hammer.common.data.id.datasources

import com.darkrockstudios.apps.hammer.common.data.ProjectDef

interface IdDatasource {
	/** Every ID this entity type currently has on disk. */
	fun findIds(projectDef: ProjectDef): Set<Int>

	fun findHighestId(projectDef: ProjectDef): Int = findIds(projectDef).maxOrNull() ?: -1
}