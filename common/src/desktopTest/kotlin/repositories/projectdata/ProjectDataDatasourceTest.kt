package repositories.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
			"/projects/Test Project/project_data.toml",
			path.toString().replace('\\', '/'),
		)
	}
}
