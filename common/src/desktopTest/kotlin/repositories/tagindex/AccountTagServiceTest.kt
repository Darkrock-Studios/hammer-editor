package repositories.tagindex

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasDatasource
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.StoryIdeaCodec
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeasSyncDatasource
import com.darkrockstudios.apps.hammer.common.data.tagindex.AccountTagService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Classical-style: real ideas on a [FakeFileSystem] via a real [IdeasRepository], real
 * `project_data.toml` files; only [ProjectsRepository] (a directory-scanner over global
 * settings) is mocked to supply the project defs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountTagServiceTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var projectsRepository: ProjectsRepository
	private lateinit var ideasRepository: IdeasRepository
	private lateinit var service: AccountTagService

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()

		val globalSettingsStore = mockk<GlobalSettingsStore>()
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")

		setupKoin()

		projectsRepository = mockk()
		every { projectsRepository.getProjects() } returns emptyList()

		val ideasDatasource = IdeasDatasource(ffs, StoryIdeaCodec(toml), globalSettingsStore)
		ideasRepository = IdeasRepository(
			ideasDatasource = ideasDatasource,
			ideasSyncDatasource = IdeasSyncDatasource(ffs, createJsonSerializer(), ideasDatasource),
			clock = Clock.System,
		)
		service = AccountTagService(ideasRepository, projectsRepository, ffs, toml)
	}

	private fun seedProject(name: String, tags: Set<String>): ProjectDef {
		val dir = "/projects/$name".toPath()
		ffs.createDirectories(dir)
		ffs.writeToml(
			dir / ProjectDataDatasource.FILENAME,
			toml,
			StoredProjectData(ProjectData(tags = tags)),
		)
		return ProjectDef(name = name, path = dir.toHPath())
	}

	@Test
	fun `Merges idea and project tags with combined frequency ranking`() = runTest {
		advanceUntilIdle()
		ideasRepository.createIdea(content = "a", tags = setOf("fantasy", "gothic"))
		ideasRepository.createIdea(content = "b", tags = setOf("fantasy"))
		val project = seedProject("One", setOf("fantasy", "ghost"))
		every { projectsRepository.getProjects() } returns listOf(project)
		service.refreshProjectTags()
		advanceUntilIdle()

		assertEquals(listOf(TagCount("fantasy", 3)), service.suggest("fan"))
		// Tie at count 1 breaks alphabetically
		assertEquals(
			listOf(TagCount("ghost", 1), TagCount("gothic", 1)),
			service.suggest("g"),
		)
	}

	@Test
	fun `Prefix matching is case-insensitive and ignores a leading hash`() = runTest {
		advanceUntilIdle()
		ideasRepository.createIdea(content = "a", tags = setOf("gothic"))
		advanceUntilIdle()

		assertEquals(listOf(TagCount("gothic", 1)), service.suggest("GO"))
		assertEquals(listOf(TagCount("gothic", 1)), service.suggest("#go"))
	}

	@Test
	fun `Blank prefix suggests nothing`() = runTest {
		advanceUntilIdle()
		ideasRepository.createIdea(content = "a", tags = setOf("gothic"))
		advanceUntilIdle()

		assertTrue(service.suggest("").isEmpty())
		assertTrue(service.suggest("  ").isEmpty())
	}

	@Test
	fun `Respects the suggestion limit`() = runTest {
		advanceUntilIdle()
		ideasRepository.createIdea(
			content = "a",
			tags = setOf("tag1", "tag2", "tag3", "tag4", "tag5", "tag6"),
		)
		advanceUntilIdle()

		assertEquals(AccountTagService.MAX_SUGGESTIONS, service.suggest("tag").size)
		assertEquals(2, service.suggest("tag", limit = 2).size)
	}

	@Test
	fun `Refresh picks up newly written project tags`() = runTest {
		advanceUntilIdle()
		val project = seedProject("One", setOf("draft"))
		every { projectsRepository.getProjects() } returns listOf(project)
		service.refreshProjectTags()
		assertEquals(listOf(TagCount("draft", 1)), service.suggest("d"))

		ffs.writeToml(
			"/projects/One".toPath() / ProjectDataDatasource.FILENAME,
			toml,
			StoredProjectData(ProjectData(tags = setOf("draft", "nano"))),
		)
		service.refreshProjectTags()

		assertEquals(listOf(TagCount("nano", 1)), service.suggest("n"))
	}

	@Test
	fun `Tolerates a project with no project_data toml`() = runTest {
		advanceUntilIdle()
		val bare = ProjectDef(name = "Bare", path = "/projects/Bare".toPath().toHPath())
		ffs.createDirectories("/projects/Bare".toPath())
		val tagged = seedProject("Tagged", setOf("fantasy"))
		every { projectsRepository.getProjects() } returns listOf(bare, tagged)

		service.refreshProjectTags()

		assertEquals(listOf(TagCount("fantasy", 1)), service.suggest("f"))
	}
}
