package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

/**
 * Told when sync gives an entity made on this device a new id, because the server already gave its
 * old one to another; see [EntitySynchronizers.reIdEntry]. Every one bound in Koin is told, once the
 * entity has its new id and before sync goes on.
 */
fun interface EntityIdChangeListener {
	suspend fun onEntityIdChanged(projectDef: ProjectDef, type: EntityType, oldId: Int, newId: Int)
}
