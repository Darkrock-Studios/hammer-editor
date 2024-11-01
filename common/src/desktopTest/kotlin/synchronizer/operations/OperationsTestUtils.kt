package synchronizer.operations

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.migrator.PROJECT_DATA_VERSION
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.CollateIdsState.CollatedIds
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityOriginalState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import kotlinx.datetime.Instant

val metadata = ProjectMetadata(
	info = Info(
		created = Instant.fromEpochSeconds(123456),
		lastAccessed = null,
		dataVersion = PROJECT_DATA_VERSION,
		serverProjectId = ProjectId("project-id")
	)
)

val projectData = ProjectSynchronizationData(
	lastId = 11,
	newIds = listOf(11),
	lastSync = Instant.fromEpochSeconds(123456),
	dirty = produceEntityStateList(1, 3, 11),
	deletedIds = setOf(8, 9)
)

/**
 * Server deleted ID 7, created ID 11
 * Client deleted ID 8, 9 created ID 11
 * Client also dirtied 1, 3, 11
 */

val entityState = ClientEntityState(produceEntityHashSet(1, 2, 3, 4, 5, 6, 7, 10))
val projId = ProjectId("project-id")

val beganResponse = ProjectSynchronizationBegan(
	syncId = "sync-id",
	lastSync = Instant.fromEpochSeconds(1234567),
	lastId = 11,
	idSequence = listOf(1, 4, 5, 11),
	deletedIds = setOf(7),
)

val collatedIds = CollatedIds(
	combinedDeletions = setOf(7, 8, 9),
	serverDeletedIds = setOf(11),
	newlyDeletedIds = setOf(9),
	dirtyEntities = produceEntityStateList(1, 3, 9, 11).toMutableList(),
)

fun produceEntityHash(id: Int) = EntityHash(id, "hash-$id")
fun produceEntityHashSet(vararg ids: Int) = ids.map { produceEntityHash(it) }.toSet()

fun produceEntityState(id: Int) = EntityOriginalState(id, "old-hash-$id")
fun produceEntityStateList(vararg ids: Int) = ids.map { produceEntityState(it) }
