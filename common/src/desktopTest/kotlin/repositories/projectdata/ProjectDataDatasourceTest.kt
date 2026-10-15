package repositories.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectdata.readStoredProjectData
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectDataDatasourceTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var datasource: ProjectDataDatasource

	private val projectDef = ProjectDef(
		name = "Test Project",
		path = "/projects/Test Project".toPath().toHPath(),
	)

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()

		fileSystem = FakeFileSystem()
		fileSystem.createDirectories("/projects/Test Project".toPath())
		toml = createTomlSerializer()

		datasource = ProjectDataDatasource(
			fileSystem = fileSystem,
			toml = toml,
			projectDef = projectDef,
		)
	}

	@Test
	fun `load returns defaults when file does not exist`() = runTest {
		val loaded = datasource.load()
		assertEquals(ProjectData(), loaded.data)
		assertNull(loaded.lastSyncedHash)
	}

	@Test
	fun `save then load roundtrips full payload`() = runTest {
		val stored = StoredProjectData(
			data = ProjectData(
				authorName = "Pat",
				theme = ProjectTheme("#FF112233", "#FFAABBCC"),
				wordCountGoal = WordCountGoal(WordCountGoal.Cadence.WEEK, 3500),
			),
			lastSyncedHash = "abc-123",
		)

		datasource.save(stored)

		val loaded = datasource.load()
		assertEquals(stored, loaded)
	}

	@Test
	fun `null sub-objects survive a roundtrip`() = runTest {
		val stored = StoredProjectData(
			data = ProjectData(authorName = "Pat", theme = null, wordCountGoal = null),
			lastSyncedHash = "h",
		)

		datasource.save(stored)

		val loaded = datasource.load()
		assertEquals(stored, loaded)
	}

	@Test
	fun `file lives in project root`() {
		val path = datasource.getPath()
		assertEquals(
			"/projects/Test Project/settings.toml",
			path.toString().replace('\\', '/'),
		)
	}

	@Test
	fun `saved file starts with the header line`() = runTest {
		datasource.save(StoredProjectData(ProjectData(authorName = "Pat")))

		val text = fileSystem.read(datasource.getPath()) { readUtf8() }
		assertTrue(text.startsWith(ProjectDataDatasource.FILE_HEADER + "\n"), text)
	}

	@Test
	fun `hand-written file with the header loads`() = runTest {
		fileSystem.write(datasource.getPath()) {
			writeUtf8(
				"""
				|${ProjectDataDatasource.FILE_HEADER}
				|lastSyncedHash = "h"
				|
				|[data]
				|authorName = "Hand"
				|
				""".trimMargin()
			)
		}

		val loaded = datasource.load()
		assertEquals(StoredProjectData(ProjectData(authorName = "Hand"), "h"), loaded)

		datasource.save(loaded)
		assertEquals(loaded, datasource.load())
	}

	@Test
	fun `corrupt file is moved aside and not overwritten by the next save`() = runTest {
		val corrupt = "[data\nauthorName = \"Broken"
		fileSystem.write(datasource.getPath()) { writeUtf8(corrupt) }

		assertEquals(StoredProjectData(), datasource.load())

		val asideFiles = corruptCopies()
		assertEquals(1, asideFiles.size)
		assertEquals(corrupt, fileSystem.read(asideFiles.single()) { readUtf8() })
		assertFalse(fileSystem.exists(datasource.getPath()))

		val fresh = StoredProjectData(ProjectData(authorName = "Fresh"))
		datasource.save(fresh)

		assertEquals(fresh, datasource.load())
		assertEquals(corrupt, fileSystem.read(corruptCopies().single()) { readUtf8() })
	}

	@Test
	fun `scope-less read moves a corrupt file aside`() {
		fileSystem.write(datasource.getPath()) { writeUtf8("data = 12") }

		assertEquals(StoredProjectData(), readStoredProjectData(projectDef, fileSystem, toml))

		assertEquals(1, corruptCopies().size)
		assertFalse(fileSystem.exists(datasource.getPath()))
	}

	@Test
	fun `each corrupt file gets its own aside copy`() {
		fileSystem.write(datasource.getPath()) { writeUtf8("first = [") }
		readStoredProjectData(projectDef, fileSystem, toml)
		fileSystem.write(datasource.getPath()) { writeUtf8("second = [") }
		readStoredProjectData(projectDef, fileSystem, toml)

		val contents = corruptCopies().map { fileSystem.read(it) { readUtf8() } }.toSet()
		assertEquals(setOf("first = [", "second = ["), contents)
	}

	@Test
	fun `read failure leaves the file in place`() {
		val original = "[data]\nauthorName = \"Locked\"\n"
		fileSystem.write(datasource.getPath()) { writeUtf8(original) }
		val failingReads = object : ForwardingFileSystem(fileSystem) {
			override fun source(file: Path): Source = throw IOException("locked: $file")
		}

		assertEquals(StoredProjectData(), readStoredProjectData(projectDef, failingReads, toml))

		assertTrue(corruptCopies().isEmpty())
		assertEquals(original, fileSystem.read(datasource.getPath()) { readUtf8() })
	}

	private fun corruptCopies() = fileSystem.list("/projects/Test Project".toPath())
		.filter { it.name.startsWith(ProjectDataDatasource.CORRUPT_PREFIX) }
}
