package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.filterScenePathsOkio
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataRepository
import com.darkrockstudios.apps.hammer.common.data.tree.NodeCoordinates
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import utils.getPrivateProperty
import verifyCoords
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class SceneEditorRepositoryMoveTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var projectPath: HPath
	private lateinit var projectsRepo: ProjectsRepository
	private lateinit var syncDataRepository: SyncDataRepository
	private lateinit var projectDef: ProjectDef
	private lateinit var repo: SceneEditorRepository
	private lateinit var idRepository: IdRepository
	private lateinit var metadataRepository: ProjectMetadataDatasource
	private lateinit var metadataDatasource: SceneMetadataDatasource
	private lateinit var sceneDatasource: SceneDatasource
	private lateinit var sceneContentRepository: SceneContentRepository
	private lateinit var statisticsRepository: StatisticsRepository
	private var nextId = -1
	private lateinit var toml: Toml

	private fun claimId(): Int {
		val id = nextId
		nextId++
		return id
	}

	private fun verify(
		node: TreeNode<SceneItem>,
		ffs: FakeFileSystem,
		print: Boolean, vararg ids: Int
	) {
		assertEquals(ids.size, node.children().size)

		if (print) {
			node.children().forEachIndexed { index, childNode ->
				println("$index - ${childNode.value.id}")
			}
		}

		node.children().forEachIndexed { index, child ->
			assertEquals(index, child.value.order, "Out of order")
			assertEquals(ids[index], child.value.id, "IDs are out of order")

			// Check mem to filesystem
			val scenePath = repo.getSceneFilePath(child.value.id)
			assertTrue(ffs.exists(scenePath.toOkioPath()))
		}

		// Check file system to mem
		val nodesById = node.children().associateBy({ it.value.id }, { it.value })
		val scenePath = repo.getSceneFilePath(node.value.id)
		ffs.list(scenePath.toOkioPath())
			.filterScenePathsOkio()
			.sortedBy { it.name }.forEach { childPath ->
				val sceneItem = sceneDatasource.getSceneFromPath(childPath)
				val foundItem = nodesById[sceneItem.id]
				assertNotNull(sceneItem, "File system scene didn't exist in tree")
				assertEquals(foundItem, sceneItem, "File system scene didn't match tree scene")
			}
	}

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()

		val rootDir = getDefaultRootDocumentDirectory()
		ffs.createDirectories(rootDir.toPath())

		syncDataRepository = mockk()
		every { syncDataRepository.isServerSynchronized() } returns false

		metadataRepository = mockk(relaxed = true)
		metadataDatasource = mockk(relaxed = true)

		projectsRepo = mockk()
		every { projectsRepo.getProjectsDirectory() } returns
				rootDir.toPath().div(PROJ_DIR).toHPath()

		projectPath = projectsRepo.getProjectsDirectory().toOkioPath().div(PROJECT_1_NAME).toHPath()

		projectDef = ProjectDef(
			name = PROJECT_1_NAME,
			path = projectPath
		)
		sceneDatasource = SceneDatasource(projectDef, ffs)

		statisticsRepository = mockk()

		toml = createTomlSerializer()

		nextId = -1
		idRepository = mockk()
		coEvery { idRepository.claimNextId() } answers { claimId() }
		coEvery { idRepository.findNextId() } answers {}

		createProject(ffs, PROJECT_1_NAME)

		setupKoin()

		sceneContentRepository = SceneContentRepository(
			projectDef = projectDef,
			sceneDatasource = sceneDatasource,
		)
		repo = SceneEditorRepository(
			projectDef = projectDef,
			syncDataRepository = syncDataRepository,
			idRepository = idRepository,
			sceneMetadataRepository = SceneMetadataRepository(
				projectDef = projectDef,
				sceneMetadataDatasource = metadataDatasource,
				projectMetadataDatasource = metadataRepository,
				strRes = mockk(relaxed = true),
				clock = Clock.System,
			),
			sceneContentRepository = sceneContentRepository,
			sceneMetadataDatasource = metadataDatasource,
			sceneDatasource = sceneDatasource,
			statisticsRepository = statisticsRepository,
			referenceIndexRepository = mockk(relaxed = true),
			writingSessionTracker = mockk(relaxed = true),
			clock = Clock.System,
		)

		runBlocking {
			repo.initializeSceneEditor()
		}
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		sceneContentRepository.onScopeClose(mockk())

		ffs.checkNoOpenFiles()
	}

	@Test
	fun `Verify Initial Layout`() {
		val tree = repo.getPrivateProperty<SceneEditorRepository, Tree<SceneItem>>("sceneTree")

		for (index in 0..tree.numChildrenRecursive()) {
			assertEquals(index, tree[index].value.id)
		}
	}

	private fun moveTest(
		request: MoveRequest,
		targetPosId: Int,
		leafToVerify: Int,
		print: Boolean,
		vararg ids: Int
	) = runTest {
		val tree = repo.getPrivateProperty<SceneEditorRepository, Tree<SceneItem>>("sceneTree")
		verifyCoords(tree, request.toPosition.coords, targetPosId)
		repo.moveScene(request)

		val afterTree =
			repo.getPrivateProperty<SceneEditorRepository, Tree<SceneItem>>("sceneTree")
		verify(afterTree[leafToVerify], ffs, print, *ids)
	}

	@Test
	fun `Move Scene Sibling, Higher to Lower`() {
		val moveRequest = MoveRequest(
			id = 6,
			toPosition = InsertPosition(
				coords = NodeCoordinates(
					parentIndex = 0,
					childLocalIndex = 0,
					globalIndex = 1
				),
				before = false
			)
		)
		// Initial Order: 1 2 6 7
		moveTest(moveRequest, 1, 0, false, 1, 6, 2, 7)
	}

	@Test
	fun `Move Scene Lower to Higher, After`() {
		val moveRequest = MoveRequest(
			id = 1,
			toPosition = InsertPosition(
				coords = NodeCoordinates(
					parentIndex = 0,
					childLocalIndex = 2,
					globalIndex = 6
				),
				before = false
			)
		)
		// Initial Order: 1, 2, 6, 7
		moveTest(moveRequest, 6, 0, false, 2, 6, 1, 7)
	}

	@Test
	fun `Move to Last, After`() {
		val moveRequest = MoveRequest(
			id = 1,
			toPosition = InsertPosition(
				coords = NodeCoordinates(
					parentIndex = 0,
					childLocalIndex = 3,
					globalIndex = 7
				),
				before = false
			)
		)
		moveTest(moveRequest, 7, 0, false, 2, 6, 7, 1)
	}

	@Test
	fun `Move to Last, Before`() {
		val moveRequest = MoveRequest(
			id = 1,
			toPosition = InsertPosition(
				coords = NodeCoordinates(
					parentIndex = 0,
					childLocalIndex = 3,
					globalIndex = 7
				),
				before = true
			)
		)
		moveTest(moveRequest, 7, 0, false, 2, 6, 1, 7)
	}

	@Test
	fun `Move Scene Lower to Higher, Before`() {
		val moveRequest = MoveRequest(
			id = 6,
			toPosition = InsertPosition(
				coords = NodeCoordinates(
					parentIndex = 0,
					childLocalIndex = 0,
					globalIndex = 1
				),
				before = true
			)
		)
		moveTest(moveRequest, 1, 0, false, 6, 1, 2, 7)
	}

	@Test
	fun `Move Scene Outter to Inner, After`() {
		val moveRequest = MoveRequest(
			id = 6,
			toPosition = InsertPosition(
				coords = NodeCoordinates(
					parentIndex = 2,
					childLocalIndex = 0,
					globalIndex = 3
				),
				before = false
			)
		)
		moveTest(moveRequest, 3, 2, false, 3, 6, 4, 5)
	}

	@Test
	fun `Move Scene Outter to Inner, Before`() {
		val moveRequest = MoveRequest(
			id = 6,
			toPosition = InsertPosition(
				coords = NodeCoordinates(
					parentIndex = 2,
					childLocalIndex = 0,
					globalIndex = 3
				),
				before = true
			)
		)
		// Initial Order: 3, 4, 5
		moveTest(moveRequest, 3, 2, false, 6, 3, 4, 5)
	}

	/**
	 * Test for a bug where moving a scene into a group would fail if the group's
	 * filename has different zero-padding than what the current sibling count would calculate.
	 *
	 * This can happen when:
	 * 1. A group is created when there are fewer siblings (e.g., 9 siblings = 1-digit padding)
	 * 2. More siblings are added later (e.g., now 10+ siblings = 2-digit padding expected)
	 * 3. The original group file still has 1-digit padding (e.g., "9-Group-8")
	 * 4. When trying to move a scene into the group, the code would calculate "09-Group-8"
	 *    but the actual folder is "9-Group-8"
	 *
	 * The fix resolves paths from the filesystem rather than calculating them.
	 */
	@Test
	fun `Move Scene Into Group With Mismatched Zero Padding`() = runTest {
		val scenesDir = sceneDatasource.getSceneDirectory().toOkioPath()

		// Initial structure has 4 root items (order 0-3: scene 1, group 2, scene 6, scene 7)
		// We need 10+ items for 2-digit padding
		// Manually create additional scene files to push count over 10
		for (i in 8..14) {
			val sceneFile = scenesDir.div("${i - 4}-Extra Scene $i-$i.md")
			ffs.write(sceneFile) { writeUtf8("Scene content $i") }
		}

		// Now we have 11 root items - new scenes would get 2-digit padding
		// But let's create an empty group file MANUALLY with 1-digit padding
		// to simulate a legacy file created when there were fewer siblings
		val groupId = 100 // Use a high ID to avoid conflicts
		// Use order 9 with single digit padding (no leading zero) - this mimics
		// a group created when there were fewer than 10 siblings
		val legacyGroupFolder = scenesDir.div("9-Legacy Group-$groupId")
		ffs.createDirectory(legacyGroupFolder)

		// Reinitialize the repo to pick up the manually created files
		sceneContentRepository.onScopeClose(mockk())

		sceneContentRepository = SceneContentRepository(
			projectDef = projectDef,
			sceneDatasource = sceneDatasource,
		)
		repo = SceneEditorRepository(
			projectDef = projectDef,
			syncDataRepository = syncDataRepository,
			idRepository = idRepository,
			sceneMetadataRepository = SceneMetadataRepository(
				projectDef = projectDef,
				sceneMetadataDatasource = metadataDatasource,
				projectMetadataDatasource = metadataRepository,
				strRes = mockk(relaxed = true),
				clock = Clock.System,
			),
			sceneContentRepository = sceneContentRepository,
			sceneMetadataDatasource = metadataDatasource,
			sceneDatasource = sceneDatasource,
			statisticsRepository = statisticsRepository,
			referenceIndexRepository = mockk(relaxed = true),
			writingSessionTracker = mockk(relaxed = true),
			clock = Clock.System,
		)
		repo.initializeSceneEditor()

		// Find the legacy group in the tree
		val tree = repo.getPrivateProperty<SceneEditorRepository, Tree<SceneItem>>("sceneTree")
		val legacyGroupNode = tree.findOrNull { it.id == groupId }
		assertNotNull(legacyGroupNode, "Legacy group should be found in tree")

		// Find scene 1 to move into the group
		val sceneToMove = tree.findOrNull { it.id == 1 }
		assertNotNull(sceneToMove, "Scene 1 should exist")

		// Get the group's tree index for the move request
		val groupIndex = tree.indexOf(legacyGroupNode)

		// Move scene 1 into the legacy group (which has mismatched padding)
		// This should NOT crash - before the fix it would fail with "no such file"
		val moveRequest = MoveRequest(
			id = 1,
			toPosition = InsertPosition(
				coords = NodeCoordinates(
					parentIndex = groupIndex,
					childLocalIndex = 0,
					globalIndex = groupIndex + 1
				),
				before = false
			)
		)

		// This is the critical part - the move should succeed without throwing
		repo.moveScene(moveRequest)

		// Verify the scene is now in the group
		val afterTree = repo.getPrivateProperty<SceneEditorRepository, Tree<SceneItem>>("sceneTree")
		val groupAfterMove = afterTree.findOrNull { it.id == groupId }
		assertNotNull(groupAfterMove, "Group should still exist after move")
		assertEquals(1, groupAfterMove.numChildrenImmedate(), "Group should have 1 child after move")
		assertEquals(1, groupAfterMove.children().first().value.id, "Scene 1 should be in the group")

		// Verify the file actually exists on the filesystem
		val actualGroupPath = sceneDatasource.resolveScenePathFromFilesystem(groupId)
		assertNotNull(actualGroupPath, "Group should exist on filesystem")

		val childFiles = ffs.list(actualGroupPath.toOkioPath())
			.filterScenePathsOkio()
		assertEquals(1, childFiles.size, "Group should have 1 scene file")
	}

	companion object {
		const val PROJ_DIR = "HammerProjects"
	}
}