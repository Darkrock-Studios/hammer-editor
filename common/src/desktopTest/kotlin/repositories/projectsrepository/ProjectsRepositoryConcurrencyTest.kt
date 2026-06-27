package repositories.projectsrepository

import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import createProjectDirectories
import getProjectsDirectory
import kotlinx.coroutines.test.runTest
import okio.ForwardingFileSystem
import okio.Path
import org.junit.jupiter.api.Test
import projectNames
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectsRepositoryConcurrencyTest : ProjectsRepositoryBaseTest() {

	/**
	 * Reproduces the listing-then-stat race: a project directory is deleted between
	 * `list()` and the per-entry `metadata()` call. getProjects must skip the vanished
	 * entry rather than throwing FileNotFoundException.
	 */
	@Test
	fun `Get Projects skips entries deleted during listing`() = scope.runTest {
		createProjectDirectories(ffs)

		val phantom = getProjectsDirectory().div("Vanished Project")
		val racingFs = object : ForwardingFileSystem(ffs) {
			override fun list(dir: Path): List<Path> =
				if (dir == getProjectsDirectory()) super.list(dir) + phantom else super.list(dir)
		}

		val repo = ProjectsRepository(racingFs, settingsRepo, projectsMetaDatasource)
		val projects = repo.getProjects()

		assertEquals(projectNames.size, projects.size)
		projects.forEach { project -> assertTrue(projectNames.contains(project.name)) }
	}
}
