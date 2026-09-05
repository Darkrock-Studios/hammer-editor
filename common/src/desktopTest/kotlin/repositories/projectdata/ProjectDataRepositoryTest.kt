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
	fun `updateData persists tags and they round-trip through TOML`() = runTest {
		datasource.save(StoredProjectData(ProjectData(authorName = "Pat"), "synced-hash"))

		repository.updateData { it.copy(tags = setOf("fantasy", "draft")) }

		val onDisk = datasource.load()
		assertEquals(setOf("fantasy", "draft"), onDisk.data.tags)
		assertEquals("Pat", onDisk.data.authorName)
		coVerify(exactly = 1) { syncDataDatasource.invalidateProjectHash() }
	}

	@Test
	fun `updateData persists the language and it round-trips through TOML`() = runTest {
		datasource.save(StoredProjectData(ProjectData(authorName = "Pat"), "synced-hash"))

		repository.updateData { it.copy(language = "pt-BR") }

		val onDisk = datasource.load()
		assertEquals("pt-BR", onDisk.data.language)
		assertEquals("Pat", onDisk.data.authorName)
	}

	@Test
	fun `pre-language project_data toml loads with null language`() = runTest {
		val legacyToml = """
			|[data]
			|authorName = "Legacy"
			|
		""".trimMargin()
		fileSystem.write("/projects/Test Project/project_data.toml".toPath()) {
			writeUtf8(legacyToml)
		}

		val loaded = repository.load()

		assertNull(loaded.data.language)
	}

	@Test
	fun `pre-tags project_data toml loads with empty tags`() = runTest {
		// A file written before the tags field existed.
		val legacyToml = """
			|[data]
			|authorName = "Legacy"
			|
		""".trimMargin()
		fileSystem.write("/projects/Test Project/project_data.toml".toPath()) {
			writeUtf8(legacyToml)
		}

		val loaded = repository.load()

		assertEquals("Legacy", loaded.data.authorName)
		assertEquals(emptySet(), loaded.data.tags)
	}

	@Test
	fun `updateFromSync replaces both data and the synced hash`() = runTest {
		val snapshot = repository.load().data

		repository.updateFromSync(ProjectData(authorName = "Server"), "server-hash", snapshot)

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
		val snapshot = repository.load().data
		repository.updateFromSync(ProjectData(authorName = "Server"), "server-hash", snapshot)
		repository.updateFromSync(ProjectData(authorName = "Server"), "server-hash", snapshot)

		assertEquals(
			StoredProjectData(ProjectData(authorName = "Server"), "server-hash"),
			repository.state.value,
		)
	}

	@Test
	fun `updateFromSync keeps edits made after the snapshot`() = runTest {
		val snapshot = repository.load().data

		// Lands while the sync's network round-trip is in flight.
		repository.updateData { it.copy(dictionaryWords = setOf("local"), authorName = "Edited") }

		repository.updateFromSync(
			ProjectData(authorName = "Server", language = "en", dictionaryWords = setOf("server")),
			"server-hash",
			snapshot,
		)

		assertEquals(
			StoredProjectData(
				ProjectData(authorName = "Edited", language = "en", dictionaryWords = setOf("server", "local")),
				"server-hash",
			),
			repository.state.value,
		)
	}
}
