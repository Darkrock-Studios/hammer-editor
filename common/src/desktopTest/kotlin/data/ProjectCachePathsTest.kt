package data

import com.darkrockstudios.apps.hammer.common.data.ProjectCachePaths
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import getProject1Def
import getProjectDef
import inGroup
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import com.darkrockstudios.apps.hammer.base.ProjectId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProjectCachePathsTest {

	private val fileSystem = FakeFileSystem().apply { allowSymlinks = true }

	@Test
	fun `Projects with the same name in different groups get different folders`() {
		val topLevel = getProject1Def()
		assertNotEquals(
			ProjectCachePaths.projectCacheDirectory(fileSystem, topLevel),
			ProjectCachePaths.projectCacheDirectory(fileSystem, topLevel.inGroup("Drafts")),
		)
	}

	@Test
	fun `The folder ignores how the project's path is written`() {
		val project = getProject1Def()
		val unnormalized = project.copy(path = project.path.copy(path = project.path.path + "/./"))
		assertEquals(ProjectCachePaths.cacheId(fileSystem, project), ProjectCachePaths.cacheId(fileSystem, unnormalized))
	}

	@Test
	fun `A project reached through a symlink shares its folder`() {
		val project = getProject1Def()
		val real = project.path.toOkioPath()
		fileSystem.createDirectories(real)
		val link = real.parent!!.parent!! / "linked-projects"
		fileSystem.createSymlink(link, real.parent!!)
		val throughLink = project.copy(path = (link / real.name).toHPath())

		assertEquals(ProjectCachePaths.cacheId(fileSystem, project), ProjectCachePaths.cacheId(fileSystem, throughLink))
	}

	@Test
	fun `A long name in a multi-byte script keeps the folder name under 255 bytes`() {
		val project = getProjectDef("山".repeat(85))
		// The cut at 48 characters falls between the two halves of a surrogate pair.
		val surrogates = getProjectDef("a" + "\ud83d\udcd6".repeat(64))

		listOf(project, surrogates).forEach {
			val id = ProjectCachePaths.cacheId(fileSystem, it)
			val bytes = id.encodeToByteArray().size
			assertTrue(bytes < 255, "Too long: $bytes bytes")
			assertTrue(
				id.windowed(2).none { pair -> pair[0].isHighSurrogate() && !pair[1].isLowSurrogate() },
				"Split a surrogate pair: $id",
			)
		}
		assertNotEquals(
			ProjectCachePaths.cacheId(fileSystem, project),
			ProjectCachePaths.cacheId(fileSystem, getProjectDef("山".repeat(86))),
		)
	}

	@Test
	fun `A folder is read only by the project that wrote it`() {
		val project = getProject1Def()
		val replacement = project.copy(id = ProjectId.randomUUID())
		val dir = ProjectCachePaths.projectCacheDirectory(fileSystem, project)

		val file = ProjectCachePaths.fileToWrite(fileSystem, dir, project, "a.toml")
		fileSystem.write(file) { writeUtf8("a") }
		fileSystem.write(dir / "b.toml") { writeUtf8("b") }

		assertEquals(file, ProjectCachePaths.fileToRead(fileSystem, dir, project, "a.toml"))
		assertNull(ProjectCachePaths.fileToRead(fileSystem, dir, replacement, "a.toml"))

		ProjectCachePaths.fileToWrite(fileSystem, dir, replacement, "a.toml")
		assertFalse(fileSystem.exists(dir / "a.toml"))
		assertFalse(fileSystem.exists(dir / "b.toml"))
		assertNull(ProjectCachePaths.fileToRead(fileSystem, dir, project, "b.toml"))
	}

	@Test
	fun `A cache folder outside the cache root is refused`() {
		val project = getProject1Def()
		val outside = project.path.toOkioPath()

		assertFailsWith<IllegalArgumentException> {
			ProjectCachePaths.fileToWrite(fileSystem, outside, project, "a.toml")
		}
		assertFailsWith<IllegalArgumentException> {
			ProjectCachePaths.deleteFile(fileSystem, outside, "a.toml")
		}
	}

	@Test
	fun `deleteFile says whether there was a file`() {
		val project = getProject1Def()
		val dir = ProjectCachePaths.projectCacheDirectory(fileSystem, project)
		fileSystem.write(ProjectCachePaths.fileToWrite(fileSystem, dir, project, "a.toml")) { writeUtf8("a") }

		assertTrue(ProjectCachePaths.deleteFile(fileSystem, dir, "a.toml"))
		assertFalse(ProjectCachePaths.deleteFile(fileSystem, dir, "a.toml"))
	}

	@Test
	fun `clear deletes the project's whole cache folder`() {
		val project = getProject1Def()
		val dir = ProjectCachePaths.projectCacheDirectory(fileSystem, project)
		fileSystem.write(ProjectCachePaths.fileToWrite(fileSystem, dir, project, "a.toml")) { writeUtf8("a") }

		ProjectCachePaths.clear(fileSystem, project.name, project.path)

		assertFalse(fileSystem.exists(dir))
	}

	@Test
	fun `Orphaned cache folders are deleted and owned ones kept`() {
		val kept = getProject1Def()
		val keptInGroup = kept.inGroup("Drafts")
		val gone = getProjectDef("Deleted Project")
		val root = ProjectCachePaths.projectsCacheRoot()

		listOf(kept, keptInGroup, gone).forEach {
			val dir = ProjectCachePaths.projectCacheDirectory(fileSystem, it)
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
				ProjectCachePaths.projectCacheDirectory(fileSystem, kept),
				ProjectCachePaths.projectCacheDirectory(fileSystem, keptInGroup),
				strayFile,
			),
			fileSystem.list(root).toSet(),
		)
		assertTrue(fileSystem.exists(ProjectCachePaths.projectCacheDirectory(fileSystem, kept) / "stats.toml"))
	}

	@Test
	fun `Nothing happens without a cache folder`() {
		assertEquals(0, ProjectCachePaths.removeOrphans(fileSystem, listOf(getProject1Def())))
	}
}
