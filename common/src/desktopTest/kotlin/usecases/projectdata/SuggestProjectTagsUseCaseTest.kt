package usecases.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectdata.SuggestProjectTagsUseCase
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Classical-style: real TOML files on a [FakeFileSystem]; only [ProjectsRepository] (a
 * directory-scanner over global settings) is mocked to supply the project defs.
 */
class SuggestProjectTagsUseCaseTest {

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var projectsRepository: ProjectsRepository
	private lateinit var useCase: SuggestProjectTagsUseCase

	private val current = def("Current Project")
	private val other1 = def("Other One")
	private val other2 = def("Other Two")
	private val noDataFile = def("No Data File")

	private fun def(name: String) =
		ProjectDef(name = name, path = "/projects/$name".toPath().toHPath())

	@BeforeEach
	fun setup() {
		fileSystem = FakeFileSystem()
		toml = createTomlSerializer()
		projectsRepository = mockk()
		useCase = SuggestProjectTagsUseCase(projectsRepository, fileSystem, toml)
	}

	private fun seedProject(projectDef: ProjectDef, tags: Set<String>) {
		val dir = "/projects/${projectDef.name}".toPath()
		fileSystem.createDirectories(dir)
		fileSystem.writeToml(
			dir / "project_data.toml",
			toml,
			StoredProjectData(ProjectData(tags = tags))
		)
	}

	@Test
	fun `unions tags across other projects, excluding the current one`() = runTest {
		seedProject(current, setOf("own-tag"))
		seedProject(other1, setOf("fantasy", "draft"))
		seedProject(other2, setOf("fantasy", "nano"))
		every { projectsRepository.getProjects() } returns listOf(current, other1, other2)

		val tags = useCase.tagsFromOtherProjects(exclude = current)

		assertEquals(setOf("fantasy", "draft", "nano"), tags)
	}

	@Test
	fun `tolerates a project with no project_data toml`() = runTest {
		seedProject(other1, setOf("fantasy"))
		fileSystem.createDirectories("/projects/${noDataFile.name}".toPath())
		every { projectsRepository.getProjects() } returns listOf(current, other1, noDataFile)

		val tags = useCase.tagsFromOtherProjects(exclude = current)

		assertEquals(setOf("fantasy"), tags)
	}

	@Test
	fun `returns empty when there are no other projects`() = runTest {
		every { projectsRepository.getProjects() } returns listOf(current)

		assertEquals(emptySet(), useCase.tagsFromOtherProjects(exclude = current))
	}
}
