package data

import com.darkrockstudios.apps.hammer.common.data.ProjectCachePaths
import getProject1Def
import getProjectDef
import inGroup
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProjectCachePathsTest {

	@Test
	fun `Projects with the same name in different groups get different folders`() {
		val topLevel = getProject1Def()
		assertNotEquals(
			ProjectCachePaths.projectCacheDirectory(topLevel),
			ProjectCachePaths.projectCacheDirectory(topLevel.inGroup("Drafts")),
		)
	}

	@Test
	fun `The folder ignores how the project's path is written`() {
		val project = getProject1Def()
		val unnormalized = project.copy(path = project.path.copy(path = project.path.path + "/./"))
		assertEquals(ProjectCachePaths.cacheId(project), ProjectCachePaths.cacheId(unnormalized))
	}

	@Test
	fun `Orphaned cache folders are deleted and owned ones kept`() {
		val fileSystem = FakeFileSystem()
		val kept = getProject1Def()
		val keptInGroup = kept.inGroup("Drafts")
		val gone = getProjectDef("Deleted Project")
		val root = ProjectCachePaths.projectsCacheRoot()

		listOf(kept, keptInGroup, gone).forEach {
			val dir = ProjectCachePaths.projectCacheDirectory(it)
			fileSystem.createDirectories(dir)
			fileSystem.write(dir / "stats.toml") { writeUtf8("") }
		}
		val legacy = root / kept.name
		fileSystem.createDirectories(legacy)
		fileSystem.write(legacy / "references.toml") { writeUtf8("") }
		val strayFile = root / "not-a-folder"
		fileSystem.write(strayFile) { writeUtf8("") }

		val removed = ProjectCachePaths.removeOrphans(fileSystem, listOf(kept, keptInGroup))

		assertEquals(2, removed)
		assertEquals(
			setOf(
				ProjectCachePaths.projectCacheDirectory(kept),
				ProjectCachePaths.projectCacheDirectory(keptInGroup),
				strayFile,
			),
			fileSystem.list(root).toSet(),
		)
		assertTrue(fileSystem.exists(ProjectCachePaths.projectCacheDirectory(kept) / "stats.toml"))
	}

	@Test
	fun `Nothing happens without a cache folder`() {
		assertEquals(0, ProjectCachePaths.removeOrphans(FakeFileSystem(), listOf(getProject1Def())))
	}
}
