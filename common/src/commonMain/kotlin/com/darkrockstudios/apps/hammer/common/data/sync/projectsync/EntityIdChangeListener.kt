package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

/**
 * An entity of [type] made on this device that sync gave a new id: [oldId] became [newId].
 * [rewrittenReferrers] are the ids of other entities whose content was rewritten to point at [newId].
 */
data class EntityIdChange(
	val type: EntityType,
	val oldId: Int,
	val newId: Int,
	val rewrittenReferrers: Set<Int> = emptySet(),
)

/**
 * Told when sync gives entities made on this device new ids, because the server already gave their old
 * ones to others; see [EntitySynchronizers.notifyEntityIdsChanged]. Every one bound in Koin is told, once
 * a sync, with the changes in the order they were made, after the entities have their new ids and before
 * sync goes on.
 */
fun interface EntityIdChangeListener {
	suspend fun onEntityIdsChanged(projectDef: ProjectDef, changes: List<EntityIdChange>)
}
