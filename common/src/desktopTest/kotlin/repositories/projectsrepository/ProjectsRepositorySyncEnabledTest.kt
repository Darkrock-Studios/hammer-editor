package repositories.projectsrepository

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectsRepositorySyncEnabledTest : ProjectsRepositoryBaseTest() {

	@Test
	fun `A project file without the field reads as enabled`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)

		assertFalse(ffs.read(def.path.toOkioPath() / ProjectMetadata.FILENAME) { readUtf8() }.contains("syncEnabled"))
		assertTrue(projectsRepository().isSyncEnabled(def))
	}

	@Test
	fun `A missing project file reads as enabled`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		ffs.delete(def.path.toOkioPath() / ProjectMetadata.FILENAME)

		assertTrue(projectsRepository().isSyncEnabled(def))
	}

	@Test
	fun `Set sync enabled persists to the project file and keeps the rest`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val repo = projectsRepository()
		val before = repo.loadProjectMetadata(def).info

		repo.setSyncEnabled(def, false)

		assertFalse(projectsRepository().isSyncEnabled(def))
		assertEquals(before.copy(syncEnabled = false), repo.loadProjectMetadata(def).info)

		repo.setSyncEnabled(def, true)
		assertTrue(repo.isSyncEnabled(def))
	}

	@Test
	fun `Sync enabled updates emit the current value then each change`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val repo = projectsRepository()

		val seen = mutableListOf<Boolean>()
		val job = launch { repo.syncEnabledUpdates(def).take(3).toList(seen) }
		yield()
		repo.setSyncEnabled(def, false)
		yield()
		repo.setSyncEnabled(def, true)
		job.join()

		assertEquals(listOf(true, false, true), seen)
	}

	@Test
	fun `Set sync enabled leaves a missing project file alone`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val path = def.path.toOkioPath() / ProjectMetadata.FILENAME
		ffs.delete(path)

		assertFalse(projectsRepository().setSyncEnabled(def, false))
		assertFalse(ffs.exists(path))
	}
}
