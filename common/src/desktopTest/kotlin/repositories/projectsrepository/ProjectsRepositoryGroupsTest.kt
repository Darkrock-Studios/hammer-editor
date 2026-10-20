package repositories.projectsrepository

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.http.MAX_GROUP_NESTING_DEPTH
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.createProjectScope
import com.darkrockstudios.apps.hammer.common.data.groupmetadata.GroupMetadata
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupOperationFailed
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectCreationFailedException
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectRenameFailed
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.zip.unzipToDirectory
import createGroupDirectory
import createNestedProjectDirectory
import getProjectsDirectory
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test
import testGroupId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectsRepositoryGroupsTest : ProjectsRepositoryBaseTest() {
	private val root: Path get() = getProjectsDirectory()

	private fun isMarkedGroup(dir: Path): Boolean = ffs.exists(dir / GroupMetadata.FILENAME)

	private fun isProject(dir: Path): Boolean = ffs.exists(dir / ProjectMetadata.FILENAME)

	private fun ProjectsRepository.group(name: String): GroupDef =
		getProjectTree().groups.single { it.name == name }

	private fun <T> withProjectOpen(projectDef: ProjectDef, block: () -> T): T {
		val projectScope = createProjectScope(projectDef)
		return try {
			block()
		} finally {
			projectScope.close()
		}
	}

	private fun assertGroupFailure(expected: GroupOperationFailed.Reason, result: CResult<*>) {
		assertTrue(isFailure(result))
		assertNotNull(result.displayMessage)
		assertEquals(expected, assertIs<GroupOperationFailed>(result.exception).reason)
	}

	private fun assertProjectFailure(expected: ProjectRenameFailed.Reason, result: CResult<*>) {
		assertTrue(isFailure(result))
		assertNotNull(result.displayMessage)
		assertEquals(expected, assertIs<ProjectRenameFailed>(result.exception).reason)
	}

	/** Marked groups `Level 1` to `Level [levels]`, each inside the one before. */
	private fun createGroupChain(levels: Int): List<Path> =
		(1..levels).runningFold(root) { parent, level -> createGroupDirectory(ffs, "Level $level", parent) }.drop(1)

	// Discovery

	@Test
	fun `The tree places each project in the group that holds it`() = scope.runTest {
		val series = createGroupDirectory(ffs, "Series")
		val trilogy = createGroupDirectory(ffs, "Trilogy", parent = series)
		val book = createNestedProjectDirectory(ffs, "Book 1", trilogy)
		val prequel = createNestedProjectDirectory(ffs, "Prequel", series)
		val loose = createNestedProjectDirectory(ffs, "Loose", root)
		val repo = projectsRepository()

		val tree = repo.getProjectTree()

		assertEquals(
			listOf(
				GroupDef(testGroupId("Series"), "Series", series.toHPath(), parentId = null, description = null),
				GroupDef(testGroupId("Trilogy"), "Trilogy", trilogy.toHPath(), testGroupId("Series"), description = null),
			),
			tree.groups,
		)
		assertEquals(
			mapOf(book.id to testGroupId("Trilogy"), prequel.id to testGroupId("Series"), loose.id to null),
			tree.placement,
		)
		assertEquals(setOf(book, prequel, loose), repo.getProjects().toSet())
		assertEquals(book, repo.findProject("Book 1"))
		assertEquals(book, repo.findProject(book.id))
		assertEquals(tree.groups[1], repo.findGroup(testGroupId("Trilogy")))
	}

	@Test
	fun `Folders holding no project are not groups and stay unmarked`() = scope.runTest {
		val empty = root / "Empty"
		ffs.createDirectories(empty)
		val unrelated = root / "Photos"
		ffs.createDirectories(unrelated / "2024")
		ffs.write(unrelated / "cat.jpg") { writeUtf8("") }
		val repo = projectsRepository()

		val tree = repo.getProjectTree()

		assertEquals(emptyList(), tree.groups)
		assertFalse(isMarkedGroup(empty))
		assertFalse(isMarkedGroup(unrelated))
		assertFalse(isMarkedGroup(unrelated / "2024"))
	}

	@Test
	fun `An empty marked folder is a group`() = scope.runTest {
		createGroupDirectory(ffs, "Someday")
		val repo = projectsRepository()

		val tree = repo.getProjectTree()

		assertEquals(listOf("Someday"), tree.groups.map { it.name })
	}

	@Test
	fun `A folder holding a project is marked as a group with a stable id`() = scope.runTest {
		val folder = root / "Trilogy"
		val book = createNestedProjectDirectory(ffs, "Book 1", folder)
		val repo = projectsRepository()

		val first = repo.getProjectTree()
		val second = repo.getProjectTree()

		val group = first.groups.single()
		assertEquals("Trilogy", group.name)
		assertTrue(isMarkedGroup(folder))
		assertEquals(first.groups, second.groups)
		assertEquals(group.id, second.placement[book.id])
	}

	@Test
	fun `A corrupt group marker is healed`() = scope.runTest {
		val trilogy = createGroupDirectory(ffs, "Trilogy")
		ffs.write(trilogy / GroupMetadata.FILENAME) { writeUtf8("not [valid toml") }
		val repo = projectsRepository()

		val healed = repo.getProjectTree().groups.single()

		assertEquals(healed, repo.getProjectTree().groups.single())
	}

	@Test
	fun `A copied group folder gets its own id`() = scope.runTest {
		val sharedId = GroupId.randomUUID()
		createGroupDirectory(ffs, "Original", groupId = sharedId)
		createGroupDirectory(ffs, "Copy", groupId = sharedId)
		val repo = projectsRepository()

		val groups = repo.getProjectTree().groups

		assertEquals(2, groups.map { it.id }.toSet().size)
		assertTrue(groups.any { it.id == sharedId })
		assertEquals(groups, repo.getProjectTree().groups)
	}

	@Test
	fun `Groups nest at most eight deep`() = scope.runTest {
		val chain = createGroupChain(MAX_GROUP_NESTING_DEPTH)
		val tooDeep = chain.last() / "Level 9"
		createNestedProjectDirectory(ffs, "Buried", tooDeep)
		val repo = projectsRepository()

		val tree = repo.getProjectTree()

		assertEquals(MAX_GROUP_NESTING_DEPTH, tree.groups.size)
		assertFalse(isMarkedGroup(tooDeep))
		assertNull(repo.findProject("Buried"))
	}

	@Test
	fun `A project sits directly in the deepest group`() = scope.runTest {
		val chain = createGroupChain(MAX_GROUP_NESTING_DEPTH)
		val deep = createNestedProjectDirectory(ffs, "Deep", chain.last())
		val repo = projectsRepository()

		val tree = repo.getProjectTree()

		assertEquals(testGroupId("Level $MAX_GROUP_NESTING_DEPTH"), tree.placement[deep.id])
	}

	@Test
	fun `Healing re-marks project folders inside groups`() = scope.runTest {
		val trilogy = createGroupDirectory(ffs, "Trilogy")
		val lost = trilogy / "Lost"
		ffs.createDirectories(lost / SceneDatasource.SCENE_DIRECTORY)
		val repo = projectsRepository()

		val healed = repo.healUnmarkedProjectDirectories().single()

		assertEquals(lost.toHPath(), healed.path)
		assertTrue(isProject(lost))
		assertEquals(testGroupId("Trilogy"), repo.getProjectTree().placement[healed.id])
	}

	// Projects in groups

	@Test
	fun `A project created in a group lives in its folder`() = scope.runTest {
		createGroupDirectory(ffs, "Trilogy")
		val repo = projectsRepository()
		val trilogy = repo.group("Trilogy")

		val created = repo.createProject("Book 1", seedDefaultLanguage = false, group = trilogy)

		assertTrue(isSuccess(created))
		assertEquals((root / "Trilogy" / "Book 1").toHPath(), created.data.path)
		assertEquals(trilogy.id, repo.getProjectTree().placement[created.data.id])
	}

	@Test
	fun `A project name is taken by a project in any group`() = scope.runTest {
		val trilogyDir = createGroupDirectory(ffs, "Trilogy")
		createNestedProjectDirectory(ffs, "Book 1", trilogyDir)
		createNestedProjectDirectory(ffs, "Standalone", root)
		val repo = projectsRepository()
		val trilogy = repo.group("Trilogy")

		val atTopLevel = repo.createProject("Book 1", seedDefaultLanguage = false)
		val inGroup = repo.createProject("Standalone", seedDefaultLanguage = false, group = trilogy)

		assertIs<ProjectCreationFailedException>((atTopLevel as ClientResult.Failure).exception)
		assertNotNull(atTopLevel.displayMessage)
		assertTrue(isFailure(inGroup))
		assertFalse(ffs.exists(root / "Book 1"))
		assertFalse(ffs.exists(trilogyDir / "Standalone"))
	}

	@Test
	fun `A project is not created in a group that is gone`() = scope.runTest {
		val trilogyDir = createGroupDirectory(ffs, "Trilogy")
		val repo = projectsRepository()
		val trilogy = repo.group("Trilogy")
		ffs.deleteRecursively(trilogyDir)

		val created = repo.createProject("Book 1", seedDefaultLanguage = false, group = trilogy)

		assertTrue(isFailure(created))
		assertFalse(ffs.exists(trilogyDir))
	}

	@Test
	fun `A project moves between groups and back to the top level`() = scope.runTest {
		val drafts = createGroupDirectory(ffs, "Drafts")
		val published = createGroupDirectory(ffs, "Published")
		val book = createNestedProjectDirectory(ffs, "Book", drafts)
		val repo = projectsRepository()

		val moved = repo.moveProject(book, repo.group("Published"))

		assertTrue(isSuccess(moved))
		assertEquals(book.copy(path = (published / "Book").toHPath()), moved.data)
		assertEquals(testGroupId("Published"), repo.getProjectTree().placement[book.id])

		val toTop = repo.moveProject(moved.data, null)

		assertTrue(isSuccess(toTop))
		assertEquals((root / "Book").toHPath(), toTop.data.path)
		val placement = repo.getProjectTree().placement
		assertTrue(book.id in placement)
		assertNull(placement[book.id])
		assertTrue(isProject(root / "Book"))
	}

	@Test
	fun `A project in use is not moved`() = scope.runTest {
		createGroupDirectory(ffs, "Published")
		val book = createNestedProjectDirectory(ffs, "Book", root)
		val repo = projectsRepository()
		val published = repo.group("Published")

		val result = withProjectOpen(book) { repo.moveProject(book, published) }

		assertProjectFailure(ProjectRenameFailed.Reason.ProjectInUse, result)
		assertTrue(isProject(root / "Book"))
	}

	@Test
	fun `A project is not moved onto a folder of the same name`() = scope.runTest {
		val drafts = createGroupDirectory(ffs, "Drafts")
		val other = createGroupDirectory(ffs, "Other")
		val book = createNestedProjectDirectory(ffs, "Book", drafts)
		ffs.createDirectories(other / "Book")
		val repo = projectsRepository()

		val result = repo.moveProject(book, repo.group("Other"))

		assertProjectFailure(ProjectRenameFailed.Reason.AlreadyExists, result)
		assertTrue(isProject(drafts / "Book"))
	}

	@Test
	fun `A project is not moved into a group that is gone`() = scope.runTest {
		val gone = createGroupDirectory(ffs, "Gone")
		val book = createNestedProjectDirectory(ffs, "Book", root)
		val repo = projectsRepository()
		val goneGroup = repo.group("Gone")
		ffs.deleteRecursively(gone)

		val result = repo.moveProject(book, goneGroup)

		assertProjectFailure(ProjectRenameFailed.Reason.TargetMissing, result)
		assertTrue(isProject(root / "Book"))
	}

	@Test
	fun `A project in a group is renamed in place`() = scope.runTest {
		val trilogy = createGroupDirectory(ffs, "Trilogy")
		val book = createNestedProjectDirectory(ffs, "Book 1", trilogy)
		val repo = projectsRepository()

		val renamed = repo.renameProject(book, "Book One")

		assertTrue(isSuccess(renamed))
		assertEquals(ProjectDef(book.id, "Book One", (trilogy / "Book One").toHPath()), renamed.data)
		assertEquals(testGroupId("Trilogy"), repo.getProjectTree().placement[book.id])
	}

	@Test
	fun `A project cannot take the name of a project in another group`() = scope.runTest {
		val trilogy = createGroupDirectory(ffs, "Trilogy")
		createNestedProjectDirectory(ffs, "Book 1", trilogy)
		val other = createNestedProjectDirectory(ffs, "Other", root)
		val repo = projectsRepository()

		val result = repo.renameProject(other, "Book 1")

		assertProjectFailure(ProjectRenameFailed.Reason.AlreadyExists, result)
		assertTrue(isProject(root / "Other"))
	}

	@Test
	fun `Deleting a project in a group removes only its folder`() = scope.runTest {
		val trilogy = createGroupDirectory(ffs, "Trilogy")
		val book = createNestedProjectDirectory(ffs, "Book 1", trilogy)
		val repo = projectsRepository()

		assertTrue(repo.deleteProject(book))

		assertFalse(ffs.exists(trilogy / "Book 1"))
		assertTrue(isMarkedGroup(trilogy))
	}

	@Test
	fun `Backing up a project in a group archives its own folder`() = scope.runTest {
		val trilogy = createGroupDirectory(ffs, "Trilogy")
		val book = createNestedProjectDirectory(ffs, "Book 1", trilogy)
		val scene = book.path.toOkioPath() / SceneDatasource.SCENE_DIRECTORY / "0-Opening-1.md"
		ffs.createDirectories(scene.parent!!)
		ffs.write(scene) { writeUtf8("It begins.") }
		val repo = projectsRepository()

		val backup = assertNotNull(backupService(repo).createBackup(book))

		val restored = "/restored".toPath()
		unzipToDirectory(ffs, backup.path.toOkioPath(), restored)
		assertTrue(ffs.exists(restored / "Book 1" / SceneDatasource.SCENE_DIRECTORY / "0-Opening-1.md"))
	}

	// Group operations

	@Test
	fun `A created group is listed with its description`() = scope.runTest {
		val repo = projectsRepository()
		val series = assertIs<ClientResult.Success<GroupDef>>(repo.createGroup("Series", parent = null)).data

		val created = repo.createGroup("Trilogy", series, description = "Three books")

		assertTrue(isSuccess(created))
		assertEquals(created.data, repo.findGroup(created.data.id))
		assertEquals(series.id, created.data.parentId)
		assertEquals("Three books", created.data.description)
	}

	@Test
	fun `A group is not created below the deepest level`() = scope.runTest {
		createGroupChain(MAX_GROUP_NESTING_DEPTH)
		val repo = projectsRepository()
		val deepest = repo.group("Level $MAX_GROUP_NESTING_DEPTH")

		val result = repo.createGroup("Too Deep", deepest)

		assertGroupFailure(GroupOperationFailed.Reason.TooDeep, result)
		assertFalse(ffs.exists(deepest.path.toOkioPath() / "Too Deep"))
	}

	@Test
	fun `A group is not created over an existing folder`() = scope.runTest {
		createNestedProjectDirectory(ffs, "Taken", root)
		val repo = projectsRepository()

		val result = repo.createGroup("Taken", parent = null)

		assertGroupFailure(GroupOperationFailed.Reason.AlreadyExists, result)
		assertFalse(isMarkedGroup(root / "Taken"))
	}

	@Test
	fun `Group names follow the project name rules`() = scope.runTest {
		createGroupDirectory(ffs, "Trilogy")
		val repo = projectsRepository()

		val created = repo.createGroup("bad~name", parent = null)
		val renamed = repo.renameGroup(repo.group("Trilogy"), "  ")

		assertGroupFailure(GroupOperationFailed.Reason.InvalidName, created)
		assertGroupFailure(GroupOperationFailed.Reason.InvalidName, renamed)
		assertFalse(ffs.exists(root / "bad~name"))
		assertTrue(isMarkedGroup(root / "Trilogy"))
	}

	@Test
	fun `A renamed group keeps its id and contents`() = scope.runTest {
		val trilogy = createGroupDirectory(ffs, "Trilogy")
		val book = createNestedProjectDirectory(ffs, "Book 1", trilogy)
		val repo = projectsRepository()

		val renamed = repo.renameGroup(repo.group("Trilogy"), "The Trilogy")

		assertTrue(isSuccess(renamed))
		val tree = repo.getProjectTree()
		assertEquals(listOf(renamed.data), tree.groups)
		assertEquals(testGroupId("Trilogy"), renamed.data.id)
		assertEquals(renamed.data.id, tree.placement[book.id])
		assertTrue(isProject(root / "The Trilogy" / "Book 1"))
	}

	@Test
	fun `A group cannot take a sibling's name`() = scope.runTest {
		createGroupDirectory(ffs, "Trilogy")
		createGroupDirectory(ffs, "Shorts")
		val repo = projectsRepository()

		val result = repo.renameGroup(repo.group("Trilogy"), "Shorts")

		assertGroupFailure(GroupOperationFailed.Reason.AlreadyExists, result)
		assertTrue(isMarkedGroup(root / "Trilogy"))
	}

	@Test
	fun `A group holding a project in use is not renamed`() = scope.runTest {
		val series = createGroupDirectory(ffs, "Series")
		val book = createNestedProjectDirectory(ffs, "Book 1", createGroupDirectory(ffs, "Trilogy", series))
		val repo = projectsRepository()

		val result = withProjectOpen(book) { repo.renameGroup(repo.group("Series"), "Saga") }

		assertGroupFailure(GroupOperationFailed.Reason.GroupInUse, result)
		assertTrue(isProject(book.path.toOkioPath()))
	}

	@Test
	fun `A group moves into another group with its contents`() = scope.runTest {
		val trilogy = createGroupDirectory(ffs, "Trilogy")
		createGroupDirectory(ffs, "Notes", trilogy)
		val book = createNestedProjectDirectory(ffs, "Book 1", trilogy)
		val series = createGroupDirectory(ffs, "Series")
		val repo = projectsRepository()

		val moved = repo.moveGroup(repo.group("Trilogy"), repo.group("Series"))

		assertTrue(isSuccess(moved))
		assertEquals((series / "Trilogy").toHPath(), moved.data.path)
		assertEquals(testGroupId("Series"), moved.data.parentId)
		val tree = repo.getProjectTree()
		assertEquals(3, tree.depthOf(testGroupId("Notes")))
		assertEquals(testGroupId("Trilogy"), tree.placement[book.id])
	}

	@Test
	fun `A group cannot move into itself or below itself`() = scope.runTest {
		val trilogy = createGroupDirectory(ffs, "Trilogy")
		createGroupDirectory(ffs, "Notes", trilogy)
		val repo = projectsRepository()
		val trilogyDef = repo.group("Trilogy")

		val intoItself = repo.moveGroup(trilogyDef, trilogyDef)
		val intoChild = repo.moveGroup(trilogyDef, repo.group("Notes"))

		assertGroupFailure(GroupOperationFailed.Reason.WouldCycle, intoItself)
		assertGroupFailure(GroupOperationFailed.Reason.WouldCycle, intoChild)
		assertTrue(isMarkedGroup(trilogy / "Notes"))
	}

	@Test
	fun `A group is not moved where its subgroups would be too deep`() = scope.runTest {
		createGroupChain(MAX_GROUP_NESTING_DEPTH - 1)
		val outer = createGroupDirectory(ffs, "Outer")
		createGroupDirectory(ffs, "Inner", outer)
		val repo = projectsRepository()
		val outerDef = repo.group("Outer")

		val tooDeep = repo.moveGroup(outerDef, repo.group("Level ${MAX_GROUP_NESTING_DEPTH - 1}"))
		val deepest = repo.moveGroup(outerDef, repo.group("Level ${MAX_GROUP_NESTING_DEPTH - 2}"))

		assertGroupFailure(GroupOperationFailed.Reason.TooDeep, tooDeep)
		assertTrue(isSuccess(deepest))
		assertEquals(MAX_GROUP_NESTING_DEPTH, repo.getProjectTree().depthOf(testGroupId("Inner")))
	}

	@Test
	fun `A group holding a project in use is not moved`() = scope.runTest {
		val trilogy = createGroupDirectory(ffs, "Trilogy")
		val book = createNestedProjectDirectory(ffs, "Book 1", trilogy)
		createGroupDirectory(ffs, "Series")
		val repo = projectsRepository()

		val result = withProjectOpen(book) { repo.moveGroup(repo.group("Trilogy"), repo.group("Series")) }

		assertGroupFailure(GroupOperationFailed.Reason.GroupInUse, result)
		assertTrue(isProject(trilogy / "Book 1"))
	}

	@Test
	fun `Deleting a group moves its contents up one level`() = scope.runTest {
		val series = createGroupDirectory(ffs, "Series")
		val trilogy = createGroupDirectory(ffs, "Trilogy", series)
		createGroupDirectory(ffs, "Notes", trilogy)
		val book = createNestedProjectDirectory(ffs, "Book 1", trilogy)
		ffs.write(trilogy / ".DS_Store") { writeUtf8("") }
		val repo = projectsRepository()

		val result = repo.deleteGroup(repo.group("Trilogy"))

		assertTrue(isSuccess(result))
		assertFalse(ffs.exists(trilogy))
		val tree = repo.getProjectTree()
		assertEquals(testGroupId("Series"), tree.placement[book.id])
		assertEquals(testGroupId("Series"), tree.group(testGroupId("Notes"))?.parentId)
		assertNull(tree.group(testGroupId("Trilogy")))
	}

	@Test
	fun `Deleting a group whose contents collide with its parent's moves nothing`() = scope.runTest {
		val series = createGroupDirectory(ffs, "Series")
		createGroupDirectory(ffs, "Notes", series)
		val trilogy = createGroupDirectory(ffs, "Trilogy", series)
		createGroupDirectory(ffs, "Notes", trilogy, groupId = GroupId.randomUUID())
		createNestedProjectDirectory(ffs, "Book 1", trilogy)
		val repo = projectsRepository()

		val result = repo.deleteGroup(repo.group("Trilogy"))

		assertGroupFailure(GroupOperationFailed.Reason.AlreadyExists, result)
		assertTrue(isMarkedGroup(trilogy))
		assertTrue(isMarkedGroup(trilogy / "Notes"))
		assertTrue(isProject(trilogy / "Book 1"))
	}

	@Test
	fun `A group holding a project in use is not deleted`() = scope.runTest {
		val trilogy = createGroupDirectory(ffs, "Trilogy")
		val book = createNestedProjectDirectory(ffs, "Book 1", trilogy)
		val repo = projectsRepository()

		val result = withProjectOpen(book) { repo.deleteGroup(repo.group("Trilogy")) }

		assertGroupFailure(GroupOperationFailed.Reason.GroupInUse, result)
		assertTrue(isProject(trilogy / "Book 1"))
		assertFalse(ffs.exists(root / "Book 1"))
	}

	@Test
	fun `Description, id and collapsed state are stored without clobbering each other`() = scope.runTest {
		val repo = projectsRepository()
		val created = assertIs<ClientResult.Success<GroupDef>>(
			repo.createGroup("Trilogy", parent = null, description = "Three books")
		).data
		val newId = GroupId.randomUUID()

		repo.setGroupCollapsed(created, true)
		val adopted = repo.setGroupId(created, newId)
		val described = repo.updateGroupDescription(adopted, "Four books")

		val listed = repo.getProjectTree().groups.single()
		assertEquals(described, listed)
		assertEquals(newId, listed.id)
		assertEquals("Four books", listed.description)
		assertTrue(repo.isGroupCollapsed(listed))

		repo.setGroupCollapsed(listed, false)

		assertFalse(repo.isGroupCollapsed(listed))
		assertEquals(listed, repo.getProjectTree().groups.single())
	}

	@Test
	fun `Collapsed state of a group that is gone reads as expanded and is not written`() = scope.runTest {
		val trilogy = createGroupDirectory(ffs, "Trilogy")
		val repo = projectsRepository()
		val trilogyDef = repo.group("Trilogy")
		ffs.deleteRecursively(trilogy)

		repo.setGroupCollapsed(trilogyDef, true)

		assertFalse(repo.isGroupCollapsed(trilogyDef))
		assertFalse(ffs.exists(trilogy))
	}
}
