package com.darkrockstudios.apps.hammer.common.data.id.datasources

import com.darkrockstudios.apps.hammer.common.data.ProjectDef

interface IdDatasource {
	/**
	 * Every ID this entity type currently has on disk, or null when its storage can't be read,
	 * in which case an absent ID is not evidence that the entity is gone.
	 */
	fun findIds(projectDef: ProjectDef): Set<Int>?

	fun findHighestId(projectDef: ProjectDef): Int = findIds(projectDef)?.maxOrNull() ?: -1
}