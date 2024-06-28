package repositories.sceneeditor

import OUT_OF_ORDER_PROJECT_NAME
import PROJECT_1_NAME
import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.migrator.PROJECT_DATA_VERSION
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ValidationFailedException
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.scenemetadatarepository.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory
import createProject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test
import utils.BaseTest
import utils.callPrivate
import utils.getPrivateProperty
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneEditorRepositoryOkioOtherTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var projectPath: HPath
	private lateinit var projectsRepo: ProjectsRepository
	private lateinit var projectSynchronizer: ClientProjectSynchronizer
	private lateinit var projectDef: ProjectDef
	private lateinit var repo: SceneEditorRepository
	private lateinit var idRepository: IdRepository
	private lateinit var metadataRepository: ProjectMetadataDatasource
	private lateinit var metadataDatasource: SceneMetadataDatasource
	private var nextId = -1
	private lateinit var toml: Toml

	private val errorException =
		ValidationFailedException(MR.strings.create_project_error_null_filename)

	private fun claimId(): Int {
		val id = nextId
		nextId++
		return id
	}

	private fun verifyTreeAndFilesystem() {
		val tree = repo.getPrivateProperty<SceneEditorRepository, Tree<SceneItem>>("sceneTree")

		// Verify tree nodes match file system nodes
		tree.filter { !it.value.isRootScene }.forEach { node ->
			val path = repo.getSceneFilePath(node.value.id)
			val fsScene = repo.getSceneFromPath(path)
			assertEquals(node.value, fsScene)
		}

		// Verify that order's match
		verifyOrder(tree.root())
	}

	private fun verifyOrder(node: TreeNode<SceneItem>) {
		if (node.children().isNotEmpty()) {
			for (ii in 0 until node.numChildrenImmedate()) {
				val child = node[ii]
				assertEquals(ii, child.value.order, "Order incorrect for ID: ${child.value.id}")
				verifyOrder(child)
			}
		}
	}

	@Before
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()

		val rootDir = getDefaultRootDocumentDirectory()
		ffs.createDirectories(rootDir.toPath())

		toml = createTomlSerializer()

		nextId = 8
		idRepository = mockk()
		coEvery { idRepository.claimNextId() } answers { claimId() }
		coEvery { idRepository.findNextId() } answers { }

		metadataRepository = mockk()
		every { metadataRepository.loadMetadata(any()) } returns
			ProjectMetadata(
				info = Info(
					created = Instant.DISTANT_FUTURE,
					lastAccessed = Instant.DISTANT_FUTURE,
					dataVersion = PROJECT_DATA_VERSION,
				)
			)

		metadataDatasource = mockk(relaxed = true)

		projectSynchronizer = mockk()
		every { projectSynchronizer.isServerSynchronized() } returns false
		//coEvery { projectSynchronizer.recordIdDeletion(any()) } just Runs

		projectsRepo = mockk()
		every { projectsRepo.getProjectsDirectory() } returns
			rootDir.toPath().div(SceneEditorRepositoryOkioMoveTest.PROJ_DIR).toHPath()

		mockkObject(ProjectsRepository.Companion)

		setupKoin()
	}

	@After
	override fun tearDown() {
		super.tearDown()
		repo.close()

		ffs.checkNoOpenFiles()
	}

	private fun configure(projectName: String) {
		projectPath = projectsRepo.getProjectsDirectory().toOkioPath().div(projectName).toHPath()

		projectDef = ProjectDef(
			name = projectName,
			path = projectPath
		)

		createProject(ffs, projectName)

		repo = SceneEditorRepositoryOkio(
			projectDef = projectDef,
			projectSynchronizer = projectSynchronizer,
			fileSystem = ffs,
			idRepository = idRepository,
			projectMetadataDatasource = metadataRepository,
			sceneMetadataDatasource = metadataDatasource,
		)
	}

	/**
	 * Load a project whose scene's have irregular order numbers
	 * On `initializeSceneEditor()` the orders will be cleaned up
	 */
	@Test
	fun `Cleanup Scene Order`() = runTest {
		configure(OUT_OF_ORDER_PROJECT_NAME)

		val beforeSceneTree: TreeNode<SceneItem> = repo.callPrivate("loadSceneTree")

		repo.initializeSceneEditor()

		val afterSceneTree: TreeNode<SceneItem> = repo.callPrivate("loadSceneTree")

		// Make sure the tree was actually changed, initializeProjectEditor()
		// should clean up this out of order project
		assertNotEquals(beforeSceneTree, afterSceneTree)

		verifyTreeAndFilesystem()

		val tree = repo.getPrivateProperty<SceneEditorRepository, Tree<SceneItem>>("sceneTree")
		tree.forEachIndexed { index, node ->
			when (index) {
				0 -> assertTrue(node.value.isRootScene)
				1 -> {
					assertEquals(2, node.value.id)
					assertEquals(0, node.value.order)
				}

				2 -> {
					assertEquals(3, node.value.id)
					assertEquals(0, node.value.order)
				}

				3 -> {
					assertEquals(5, node.value.id)
					assertEquals(1, node.value.order)
				}

				4 -> {
					assertEquals(4, node.value.id)
					assertEquals(2, node.value.order)
				}

				5 -> {
					assertEquals(1, node.value.id)
					assertEquals(1, node.value.order)
				}

				6 -> {
					assertEquals(6, node.value.id)
					assertEquals(2, node.value.order)
				}

				7 -> {
					assertEquals(7, node.value.id)
					assertEquals(3, node.value.order)
				}
			}
		}
	}

	@Test
	fun `Create Scene, Invalid Scene Name`() = runTest {
		configure(PROJECT_1_NAME)

		every { ProjectsRepository.validateFileName(any()) } returns CResult.failure(errorException)

		repo.initializeSceneEditor()

		val sceneName = "New Scene"
		val newScene = repo.createScene(null, sceneName)
		assertNull(newScene, "Scene should not have been created")
	}

	@Test
	fun `Create Scene, Under Root`() = runTest {
		configure(PROJECT_1_NAME)

		every { ProjectsRepository.validateFileName(any()) } returns CResult.success()

		repo.initializeSceneEditor()

		verifyTreeAndFilesystem()

		val sceneName = "New Scene"
		val newScene = repo.createScene(null, sceneName)
		assertNotNull(newScene)
		assertEquals(sceneName, newScene.name, "Name was incorrect")
		assertEquals(newScene.type, SceneItem.Type.Scene, "Should be scene")
		assertEquals(8, newScene.id, "ID was incorrect")
		assertEquals(4, newScene.order, "Order was incorrect")

		verifyTreeAndFilesystem()
	}

	@Test
	fun `Create Scene, In Group`() = runTest {
		configure(PROJECT_1_NAME)

		every { ProjectsRepository.validateFileName(any()) } returns CResult.success()

		repo.initializeSceneEditor()

		verifyTreeAndFilesystem()

		val sceneName = "New Scene"
		val group = repo.getSceneItemFromId(2)
		assertNotNull(group)
		assertEquals("Chapter ID 2", group.name)

		val newScene = repo.createScene(group, sceneName)
		assertNotNull(newScene)
		assertEquals(sceneName, newScene.name, "Name was incorrect")
		assertEquals(newScene.type, SceneItem.Type.Scene, "Should be scene")
		assertEquals(8, newScene.id, "ID was incorrect")
		assertEquals(3, newScene.order, "Order was incorrect")

		verifyTreeAndFilesystem()
	}

	@Test
	fun `Create Group, In Root`() = runTest {
		configure(PROJECT_1_NAME)

		every { ProjectsRepository.validateFileName(any()) } returns CResult.success()

		repo.initializeSceneEditor()
		verifyTreeAndFilesystem()

		val groupName = "New Group"
		val newGroup = repo.createGroup(null, groupName)
		assertNotNull(newGroup)
		assertEquals(newGroup.type, SceneItem.Type.Group, "Should be group")
		assertEquals(8, newGroup.id, "ID was incorrect")
		assertEquals(4, newGroup.order, "Order was incorrect")

		verifyTreeAndFilesystem()
	}

	@Test
	fun `Create Group, In Group`() = runTest {
		configure(PROJECT_1_NAME)

		every { ProjectsRepository.validateFileName(any()) } returns CResult.success()

		repo.initializeSceneEditor()

		verifyTreeAndFilesystem()

		val groupName = "New Group"
		val group = repo.getSceneItemFromId(2)
		assertNotNull(group)
		assertEquals("Chapter ID 2", group.name)

		val newGroup = repo.createGroup(group, groupName)

		assertNotNull(newGroup)
		assertEquals(groupName, newGroup.name, "Name was incorrect")
		assertEquals(newGroup.type, SceneItem.Type.Group, "Should be scene")
		assertEquals(8, newGroup.id, "ID was incorrect")
		assertEquals(3, newGroup.order, "Order was incorrect")

		verifyTreeAndFilesystem()
	}

	@Test
	fun `Delete Scene, In Root`() = runTest {
		configure(PROJECT_2_NAME)

		repo.initializeSceneEditor()

		val sceneId = 6
		val scenePreDelete = repo.getSceneItemFromId(sceneId)
		assertNotNull(scenePreDelete)

		val scenePath = repo.getSceneFilePath(scenePreDelete)

		val deleted = repo.deleteScene(scenePreDelete)
		assertTrue(deleted)

		assertFalse(ffs.exists(scenePath.toOkioPath()), "Scene file was not deleted")

		verifyTreeAndFilesystem()

		val scenePostDelete = repo.getSceneItemFromId(sceneId)
		assertNull(scenePostDelete, "Scene still existed in tree")
	}

	@Test
	fun `Delete Scene, In Group`() = runTest {
		configure(PROJECT_2_NAME)

		repo.initializeSceneEditor()

		val sceneId = 3
		val scenePreDelete = repo.getSceneItemFromId(sceneId)
		assertNotNull(scenePreDelete)

		val scenePath = repo.getSceneFilePath(scenePreDelete)

		val deleted = repo.deleteScene(scenePreDelete)
		assertTrue(deleted)

		assertFalse(ffs.exists(scenePath.toOkioPath()), "Scene file was not deleted")

		verifyTreeAndFilesystem()

		val scenePostDelete = repo.getSceneItemFromId(sceneId)
		assertNull(scenePostDelete, "Scene still existed in tree")
	}

	@Test
	fun `Delete Group, In Root, With Children`() = runTest {
		configure(PROJECT_2_NAME)

		repo.initializeSceneEditor()

		val groupId = 2
		val groupPreDelete = repo.getSceneItemFromId(groupId)
		assertNotNull(groupPreDelete)

		val groupPath = repo.getSceneFilePath(groupPreDelete)

		val deleted = repo.deleteGroup(groupPreDelete)
		assertFalse(deleted)

		assertTrue(ffs.exists(groupPath.toOkioPath()), "Group file was deleted")

		verifyTreeAndFilesystem()

		val groupPostDelete = repo.getSceneItemFromId(groupId)
		assertNotNull(groupPostDelete, "Group no longer existed in tree")
	}

	@Test
	fun `Delete Group, In Root, No Children`() = runTest {
		configure(PROJECT_2_NAME)

		repo.initializeSceneEditor()

		val groupId = 8
		val groupPreDelete = repo.getSceneItemFromId(groupId)
		assertNotNull(groupPreDelete, "No group for ID: $groupId")

		val groupPath = repo.getSceneFilePath(groupPreDelete)

		val deleted = repo.deleteGroup(groupPreDelete)
		assertTrue(deleted, "deleteGroup returned false")

		assertFalse(ffs.exists(groupPath.toOkioPath()), "Group file was not deleted")

		verifyTreeAndFilesystem()

		val groupPostDelete = repo.getSceneItemFromId(groupId)
		assertNull(groupPostDelete, "Group still existed in tree")
	}
}