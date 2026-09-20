package repositories.syncdata

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.writeJson
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityOriginalState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource.Companion.SYNC_FILE_NAME
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.loadPendingEntityCount
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The scope-less count exists so a caller that only needs the number does not have to open a whole
 * project scope for it.
 */
class PendingEntityCountTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var json: Json

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		json = createJsonSerializer()
	}

	private fun syncPath(projectDef: ProjectDef): Path = projectDef.path.toOkioPath() / SYNC_FILE_NAME

	@Test
	fun `A project that has never synced has nothing pending`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		ffs.delete(syncPath(projectDef))

		assertEquals(0, loadPendingEntityCount(projectDef, ffs, json))
		// Asking must not be what creates the journal.
		assertFalse(ffs.exists(syncPath(projectDef)))
	}

	@Test
	fun `Pending entities are the new ones plus the changed ones`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		ffs.writeJson(
			syncPath(projectDef),
			json,
			ProjectSynchronizationData(
				currentSyncId = null,
				lastId = 6,
				newIds = listOf(5, 6),
				lastSync = Instant.DISTANT_PAST,
				dirty = listOf(EntityOriginalState(1, "hash"), EntityOriginalState(5, null)),
				deletedIds = setOf(3),
			),
		)

		// Entity 5 is both new and dirty, and a deleted id is not writing waiting to upload.
		assertEquals(3, loadPendingEntityCount(projectDef, ffs, json))
	}

	@Test
	fun `A journal that cannot be read is not reported as nothing pending`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		ffs.write(syncPath(projectDef)) { writeUtf8("{ this is not json") }

		// Callers use this to decide whether local writing is safe to delete, and a corrupt
		// journal is not evidence that it is.
		assertThrows<SerializationException> { loadPendingEntityCount(projectDef, ffs, json) }
	}
}
