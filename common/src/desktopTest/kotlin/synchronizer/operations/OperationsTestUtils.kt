package synchronizer.operations

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.migrator.PROJECT_DATA_VERSION
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityOriginalState
import kotlinx.datetime.Instant

val metadata = ProjectMetadata(
	info = Info(
		created = Instant.fromEpochSeconds(123456),
		lastAccessed = null,
		dataVersion = PROJECT_DATA_VERSION,
		serverProjectId = ProjectId("project-id")
	)
)

fun produceEntityHash(id: Int) = EntityHash(id, "hash-$id")
fun produceEntityHashSet(vararg ids: Int) = ids.map { produceEntityHash(it) }.toSet()

fun produceEntityState(id: Int) = EntityOriginalState(id, "old-hash-$id")
fun produceEntityStateList(vararg ids: Int) = ids.map { produceEntityState(it) }
