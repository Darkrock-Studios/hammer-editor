package repositories.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Classical-style: drives a real [ProjectDataRepository] over a real [ProjectDataDatasource]
 * backed by a [FakeFileSystem]. Only the sibling [SyncDataDatasource] (its own persistence
 * concern) is mocked, so hash invalidation can be observed.
 */
class ProjectDataRepositoryTest : BaseTest() {

	private val projectDef = ProjectDef(
		name = "Test Project",
		path = "/projects/Test Project".toPath().toHPath(),
	)

	@MockK(relaxed = true)
	private lateinit var syncDataDatasource: SyncDataDatasource

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var datasource: ProjectDataDatasource
	private lateinit var repository: ProjectDataRepository

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)

		fileSystem = FakeFileSystem()
		fileSystem.createDirectories("/projects/Test Project".toPath())
		toml = createTomlSerializer()

		setupKoin(module {
			scope<ProjectDefScope> {
				scoped { projectDef }
				scoped { syncDataDatasource }
			}
		})

		datasource = ProjectDataDatasource(fileSystem, toml, projectDef)
		repository = ProjectDataRepository(datasource, projectDef)
	}

	@Test
	fun `load returns defaults when nothing is stored`() = runTest {
		val loaded = repository.load()

		assertEquals(ProjectData(), loaded.data)
		assertNull(loaded.lastSyncedHash)
	}

	@Test
	fun `load caches the first read`() = runTest {
		datasource.save(StoredProjectData(ProjectData(authorName = "Disk"), "h"))

		val first = repository.load()
		// A later out-of-band disk change must not be observed: load is cached.
		datasource.save(StoredProjectData(ProjectData(authorName = "Changed"), "h2"))
		val second = repository.load()

		assertEquals("Disk", first.data.authorName)
		assertEquals(first, second)
		assertEquals(first, repository.state.value)
	}

	@Test
	fun `updateData persists the edit and invalidates the project hash`() = runTest {
		datasource.save(StoredProjectData(ProjectData(authorName = "Old"), "synced-hash"))

		repository.updateData { it.copy(authorName = "New") }

		val state = repository.state.value
		assertEquals("New", state?.data?.authorName)
		// A user edit must not disturb the conflict baseline.
		assertEquals("synced-hash", state?.lastSyncedHash)
		assertEquals(StoredProjectData(ProjectData(authorName = "New"), "synced-hash"), datasource.load())
		coVerify(exactly = 1) { syncDataDatasource.invalidateProjectHash() }
	}

	@Test
	fun `updateData is a no-op when the transform changes nothing`() = runTest {
		datasource.save(StoredProjectData(ProjectData(authorName = "Same"), "h"))
		repository.load()

		repository.updateData { it }

		assertEquals("h", repository.state.value?.lastSyncedHash)
		coVerify(exactly = 0) { syncDataDatasource.invalidateProjectHash() }
	}

	@Test
	fun `updateFromSync replaces both data and the synced hash`() = runTest {
		repository.load()

		repository.updateFromSync(ProjectData(authorName = "Server"), "server-hash")

		assertEquals(
			StoredProjectData(ProjectData(authorName = "Server"), "server-hash"),
			repository.state.value,
		)
		assertEquals(
			StoredProjectData(ProjectData(authorName = "Server"), "server-hash"),
			datasource.load(),
		)
		// Sync-driven updates never invalidate the project hash.
		coVerify(exactly = 0) { syncDataDatasource.invalidateProjectHash() }
	}

	@Test
	fun `updateFromSync is idempotent`() = runTest {
		repository.updateFromSync(ProjectData(authorName = "Server"), "server-hash")
		repository.updateFromSync(ProjectData(authorName = "Server"), "server-hash")

		assertEquals(
			StoredProjectData(ProjectData(authorName = "Server"), "server-hash"),
			repository.state.value,
		)
	}
}
