package repositories.projectsrepository

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectOwner
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectsRepositoryOwnerTest : ProjectsRepositoryBaseTest() {

	@Test
	fun `A project of the account's own has no owner`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)

		assertNull(projectsRepository().getOwner(getProjectDef(PROJECT_1_NAME)))
	}

	@Test
	fun `A project created for a shared server project records its owner`() = scope.runTest {
		val repo = projectsRepository()
		val owner = ProjectOwner(7L, "Alice")

		val result = repo.createProject("Shared", seedDefaultLanguage = false, owner = owner)

		assertTrue(isSuccess(result))
		assertEquals(owner, repo.getOwner(result.data))
	}

	@Test
	fun `Set owner keeps the rest of the project file`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val repo = projectsRepository()
		val before = repo.loadProjectMetadata(def).info

		assertTrue(repo.setOwner(def, ProjectOwner(7L, "Alice")))

		assertEquals(before.copy(ownerId = 7L, ownerName = "Alice"), repo.loadProjectMetadata(def).info)
	}

	@Test
	fun `Set owner leaves a missing project file alone`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val path = def.path.toOkioPath() / ProjectMetadata.FILENAME
		ffs.delete(path)

		assertFalse(projectsRepository().setOwner(def, ProjectOwner(7L, "Alice")))
		assertFalse(ffs.exists(path))
	}
}
