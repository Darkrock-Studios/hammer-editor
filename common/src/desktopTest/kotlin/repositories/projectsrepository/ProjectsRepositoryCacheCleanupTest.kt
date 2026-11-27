package repositories.projectsrepository

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.ProjectCachePaths
import createProject
import getProjectDef
import io.mockk.every
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectsRepositoryCacheCleanupTest : ProjectsRepositoryBaseTest() {

	private fun cacheFolder(name: String) = (ProjectCachePaths.projectsCacheRoot() / name).also {
		ffs.createDirectories(it)
	}

	@Test
	fun `Deletes cache folders no project owns`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val owned = ProjectCachePaths.projectCacheDirectory(ffs, getProjectDef(PROJECT_1_NAME))
		ffs.createDirectories(owned)
		val orphan = cacheFolder("Gone-0000000000000000")

		projectsRepository().removeOrphanedProjectCaches()

		assertTrue(ffs.exists(owned))
		assertFalse(ffs.exists(orphan))
	}

	@Test
	fun `Leaves every cache while the projects directory is missing`() = scope.runTest {
		every { settings.projectsDirectory } returns "/unmounted/Hammer"
		val cache = cacheFolder("Some Project-0000000000000000")

		projectsRepository().removeOrphanedProjectCaches()

		assertTrue(ffs.exists(cache))
	}

	@Test
	fun `Leaves every cache while the projects directory holds no project`() = scope.runTest {
		val cache = cacheFolder("Some Project-0000000000000000")

		projectsRepository().removeOrphanedProjectCaches()

		assertTrue(ffs.exists(cache))
	}
}
