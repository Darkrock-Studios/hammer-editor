package synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.SceneItem.Companion.ROOT_ID
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.findById
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneSynchronizer
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import getProject1Def
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.StringResource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import utils.fromApiEntity
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class SceneSynchronizerTest : BaseTest() {

	private val def = getProject1Def()

	@MockK
	private lateinit var sceneEditorRepository: SceneEditorRepository

	@MockK
	private lateinit var sceneEditorService: SceneEditorService

	@MockK
	private lateinit var draftRepository: SceneDraftRepository

	@MockK
	private lateinit var serverProjectApi: ServerProjectApi

	@MockK
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	private val strRes: StrRes = object : StrRes {
		override suspend fun get(str: StringResource) = "test"
		override suspend fun get(str: StringResource, vararg args: Any) = "test"
	}

	private lateinit var rootNode: TreeNode<SceneItem>
	private lateinit var tree: Tree<SceneItem>

	@BeforeEach
	fun begin() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)

		tree = Tree()
		rootNode = TreeNode(rootSceneNode(def))
		tree.setRoot(rootNode)

		// Default mocks for archived scenes - returns empty/null unless overridden
		every { sceneEditorRepository.getArchivedScenes() } returns emptyList()
		every { sceneEditorRepository.getArchivedSceneFromId(any()) } returns null

		// Download path reads existing metadata for created/lastEdited fallback.
		coEvery { sceneEditorRepository.loadSceneMetadata(any()) } returns SceneMetadata()
	}

	private fun defaultSceneSynchronizer() = ClientSceneSynchronizer(
		projectDef = def,
		sceneEditorRepository = sceneEditorRepository,
		sceneEditorService = sceneEditorService,
		draftRepository = draftRepository,
		serverProjectApi = serverProjectApi,
		projectMetadataDatasource = projectMetadataDatasource,
		strRes = strRes,
	)

	@Test
	fun `Download Scene - New Scene`() = runTest {
		////////////////////
		// Setup
		val sceneId = 1
		val syncId = "syncId"
		val serverEntity = ApiProjectEntity.SceneEntity(
			id = 1,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Test Scene",
			path = listOf(0),
			content = "Scene Content",
			outline = "",
			notes = "",
		)
		val filePath = HPath("/", "", true)
		val clientEntity = SceneItem.fromApiEntity(serverEntity, def)
		val content = SceneContent(clientEntity, serverEntity.content)

		every { sceneEditorRepository.getSceneItemFromId(ROOT_ID) } returns rootSceneNode(def)
		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns null
		coEvery {
			sceneEditorRepository.createScene(
				parent = rootNode.value,
				sceneName = serverEntity.name,
				forceId = serverEntity.id,
				forceOrder = serverEntity.order
			)
		} coAnswers {
			val entityTreeNode = TreeNode(clientEntity)
			rootNode.addChild(entityTreeNode)
			clientEntity
		}
		every { sceneEditorRepository.rawTree } returns tree
		every { sceneEditorRepository.resolveScenePathFromFilesystem(clientEntity.id) } returns filePath
		coEvery { sceneEditorRepository.storeSceneMarkdownRaw(content, filePath) } returns true

		////////////////////
		// Test
		val sync = defaultSceneSynchronizer()
		sync.storeEntity(
			serverEntity = serverEntity,
			syncId = syncId,
			onLog = {}
		)

		////////////////////
		// Verify
		coVerify(exactly = 1) {
			sceneEditorRepository.createScene(
				parent = rootNode.value,
				sceneName = serverEntity.name,
				forceId = serverEntity.id,
				forceOrder = serverEntity.order
			)
		}
		coVerify(exactly = 1) { sceneEditorRepository.storeSceneMarkdownRaw(content, filePath) }
		coVerify(exactly = 1) { sceneEditorRepository.onContentChanged(content, UpdateSource.Sync) }
	}

	@Test
	fun `Download Scene - Simple update`() = runTest {
		////////////////////
		// Setup
		val sceneId = 1
		val syncId = "syncId"
		val serverEntity = ApiProjectEntity.SceneEntity(
			id = 1,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Test Scene",
			path = listOf(0),
			content = "Scene Content",
			outline = "",
			notes = "",
		)
		val oldContent = "old Scene Content"
		val clientEntity = SceneItem.fromApiEntity(serverEntity.copy(content = oldContent), def)
		val filePath = HPath("/", "", true)
		val content = SceneContent(clientEntity, serverEntity.content)

		every { sceneEditorRepository.getSceneItemFromId(ROOT_ID) } returns rootSceneNode(def)
		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns clientEntity
		every { sceneEditorRepository.rawTree } returns tree
		every { sceneEditorRepository.resolveScenePathFromFilesystem(clientEntity.id) } returns filePath
		coEvery { sceneEditorRepository.storeSceneMarkdownRaw(content, filePath) } returns true

		rootNode.addChild(TreeNode(clientEntity))

		////////////////////
		// Test
		val sync = defaultSceneSynchronizer()
		sync.storeEntity(
			serverEntity = serverEntity,
			syncId = syncId,
			onLog = {}
		)

		////////////////////
		// Verify
		coVerify(exactly = 1) { sceneEditorRepository.storeSceneMarkdownRaw(content, filePath) }
		coVerify(exactly = 1) { sceneEditorRepository.onContentChanged(content, UpdateSource.Sync) }
	}

	@Test
	fun `Download Scene - Update, move group`() = runTest {
		////////////////////
		// Setup
		val sceneId = 1
		val syncId = "syncId"
		val serverEntity = ApiProjectEntity.SceneEntity(
			id = 1,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Test Scene",
			path = listOf(0),
			content = "Scene Content",
			outline = "",
			notes = "",
		)

		val clientSceneEntity = SceneItem(
			projectDef = def,
			type = SceneItem.Type.Scene,
			id = 1,
			name = "Test Name",
			order = 0
		)

		val filePath = HPath("/", "", true)
		val content = SceneContent(clientSceneEntity, serverEntity.content)

		every { sceneEditorRepository.getSceneItemFromId(ROOT_ID) } returns rootSceneNode(def)
		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns clientSceneEntity
		every { sceneEditorRepository.rawTree } returns tree
		every { sceneEditorRepository.resolveScenePathFromFilesystem(clientSceneEntity.id) } returns filePath
		coEvery { sceneEditorRepository.storeSceneMarkdownRaw(content, filePath) } returns true

		val clientGroupEntity = SceneItem(
			projectDef = def,
			type = SceneItem.Type.Group,
			id = 2,
			name = "Group Name",
			order = 0
		)
		val groupNode = TreeNode(clientGroupEntity)
		rootNode.addChild(groupNode)

		val sceneNode = TreeNode(clientSceneEntity)
		groupNode.addChild(sceneNode)

		////////////////////
		// Test
		val sync = defaultSceneSynchronizer()
		sync.storeEntity(
			serverEntity = serverEntity,
			syncId = syncId,
			onLog = {}
		)

		////////////////////
		// Verify
		coVerify(exactly = 1) { sceneEditorRepository.storeSceneMarkdownRaw(content, filePath) }
		coVerify(exactly = 1) { sceneEditorRepository.onContentChanged(content, UpdateSource.Sync) }

		assertEquals(0, sceneNode.parent?.value?.id)
		assertEquals(0, groupNode.parent?.value?.id)
	}

	@Test
	fun `Download Group - Simple update`() = runTest {
		////////////////////
		// Setup
		val sceneId = 1
		val syncId = "syncId"
		val serverEntity = ApiProjectEntity.SceneEntity(
			id = 1,
			sceneType = ApiSceneType.Group,
			order = 0,
			name = "Test Group",
			path = listOf(0),
			content = "Scene Content",
			outline = "",
			notes = "",
		)
		val oldName = "old Group Name"
		val clientEntity = SceneItem.fromApiEntity(serverEntity, def).copy(name = oldName)

		every { sceneEditorRepository.getSceneItemFromId(ROOT_ID) } returns rootSceneNode(def)
		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns clientEntity
		every { sceneEditorRepository.rawTree } returns tree

		val entityTreeNode = TreeNode(clientEntity)
		rootNode.addChild(entityTreeNode)

		assertEquals(oldName, entityTreeNode.value.name)

		////////////////////
		// Test
		val sync = defaultSceneSynchronizer()
		sync.storeEntity(
			serverEntity = serverEntity,
			syncId = syncId,
			onLog = {}
		)

		////////////////////
		// Verify
		assertEquals(serverEntity.name, entityTreeNode.value.name)
		coVerify(exactly = 0) { sceneEditorRepository.createGroup(any(), any(), any(), any()) }
	}

	@Test
	fun `Download Group - New group`() = runTest {
		////////////////////
		// Setup
		val sceneId = 1
		val syncId = "syncId"
		val serverEntity = ApiProjectEntity.SceneEntity(
			id = 1,
			sceneType = ApiSceneType.Group,
			order = 0,
			name = "Test Group",
			path = listOf(0),
			content = "Scene Content",
			outline = "",
			notes = "",
		)
		val clientEntity = SceneItem.fromApiEntity(serverEntity, def)

		every { sceneEditorRepository.getSceneItemFromId(ROOT_ID) } returns rootSceneNode(def)
		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns null
		coEvery {
			sceneEditorRepository.createGroup(
				parent = rootNode.value,
				groupName = clientEntity.name,
				forceId = serverEntity.id,
				forceOrder = serverEntity.order
			)
		} coAnswers {
			val entityTreeNode = TreeNode(clientEntity)
			rootNode.addChild(entityTreeNode)
			clientEntity
		}
		every { sceneEditorRepository.rawTree } returns tree

		////////////////////
		// Test
		val sync = defaultSceneSynchronizer()
		sync.storeEntity(
			serverEntity = serverEntity,
			syncId = syncId,
			onLog = {}
		)

		////////////////////
		// Verify
		val newGroupNode = tree.findById(serverEntity.id)
		assertNotNull(newGroupNode)

		coVerify(exactly = 1) { sceneEditorRepository.createGroup(any(), any(), any(), any()) }
	}

	@Test
	fun `ownsEntity - includes archived scenes`() = runTest {
		////////////////////
		// Setup
		val sceneId = 1
		val archivedScene = SceneItem(
			projectDef = def,
			type = SceneItem.Type.Scene,
			id = sceneId,
			name = "Archived Scene",
			order = 0,
			archived = true,
		)

		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns null // Not in active tree
		every { sceneEditorRepository.getArchivedSceneFromId(sceneId) } returns archivedScene

		////////////////////
		// Test
		val sync = defaultSceneSynchronizer()
		val owns = sync.ownsEntity(sceneId)

		////////////////////
		// Verify
		kotlin.test.assertTrue(owns, "Should own archived scene")
	}

	@Test
	fun `getEntityHash - confirmedReferences in scene metadata change the hash`() = runTest {
		// Defends against a regression where SceneMetadata.confirmedReferences stops being
		// fed into the sync hash. If that breaks, two clients silently never converge on
		// reference state because the change-detection layer thinks nothing changed.
		val sceneId = 42
		val sceneItem = SceneItem(
			projectDef = def,
			type = SceneItem.Type.Scene,
			id = sceneId,
			name = "Scene With Refs",
			order = 0,
		)
		val filePath = HPath("/scene.md", "scene.md", false)

		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns sceneItem
		every { sceneEditorRepository.getArchivedSceneFromId(sceneId) } returns null
		every { sceneEditorRepository.resolveScenePathFromFilesystemIncludingArchived(sceneId) } returns filePath
		every { sceneEditorRepository.getScenePathSegments(filePath) } returns ScenePathSegments(listOf(0))
		coEvery { sceneEditorRepository.loadSceneMarkdownRaw(sceneItem, filePath) } returns "scene text"

		val sync = defaultSceneSynchronizer()

		coEvery { sceneEditorRepository.loadSceneMetadata(sceneId) } returns
			SceneMetadata(confirmedReferences = setOf(1, 2))
		val hashWithRefs = sync.getEntityHash(sceneId)

		coEvery { sceneEditorRepository.loadSceneMetadata(sceneId) } returns
			SceneMetadata(confirmedReferences = emptySet())
		val hashWithoutRefs = sync.getEntityHash(sceneId)

		assertNotNull(hashWithRefs)
		assertNotNull(hashWithoutRefs)
		assertNotEquals(
			hashWithRefs, hashWithoutRefs,
			"Scene sync hash must change when confirmedReferences changes, or sync will silently drop the field"
		)
	}

	@Test
	fun `getEntityHash - tags in scene metadata change the hash`() = runTest {
		// Defends against a regression where SceneMetadata.tags stops being fed into the
		// sync hash. If that breaks, two clients silently never converge on tag state
		// because the change-detection layer thinks nothing changed.
		val sceneId = 43
		val sceneItem = SceneItem(
			projectDef = def,
			type = SceneItem.Type.Scene,
			id = sceneId,
			name = "Scene With Tags",
			order = 0,
		)
		val filePath = HPath("/scene.md", "scene.md", false)

		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns sceneItem
		every { sceneEditorRepository.getArchivedSceneFromId(sceneId) } returns null
		every { sceneEditorRepository.resolveScenePathFromFilesystemIncludingArchived(sceneId) } returns filePath
		every { sceneEditorRepository.getScenePathSegments(filePath) } returns ScenePathSegments(listOf(0))
		coEvery { sceneEditorRepository.loadSceneMarkdownRaw(sceneItem, filePath) } returns "scene text"

		val sync = defaultSceneSynchronizer()

		coEvery { sceneEditorRepository.loadSceneMetadata(sceneId) } returns
			SceneMetadata(tags = setOf("important", "draft"))
		val hashWithTags = sync.getEntityHash(sceneId)

		coEvery { sceneEditorRepository.loadSceneMetadata(sceneId) } returns
			SceneMetadata(tags = emptySet())
		val hashWithoutTags = sync.getEntityHash(sceneId)

		assertNotNull(hashWithTags)
		assertNotNull(hashWithoutTags)
		assertNotEquals(
			hashWithTags, hashWithoutTags,
			"Scene sync hash must change when tags change, or sync will silently drop the field"
		)
	}

	@Test
	fun `Download Scene - stores tags from server entity into metadata`() = runTest {
		val sceneId = 44
		val syncId = "syncId"
		val serverEntity = ApiProjectEntity.SceneEntity(
			id = sceneId,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Tagged Scene",
			path = listOf(0),
			content = "Scene Content",
			outline = "",
			notes = "",
			tags = setOf("magic", "spoiler"),
		)
		val filePath = HPath("/", "", true)
		val clientEntity = SceneItem.fromApiEntity(serverEntity, def)
		val content = SceneContent(clientEntity, serverEntity.content)

		every { sceneEditorRepository.getSceneItemFromId(ROOT_ID) } returns rootSceneNode(def)
		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns null
		coEvery {
			sceneEditorRepository.createScene(
				parent = rootNode.value,
				sceneName = serverEntity.name,
				forceId = serverEntity.id,
				forceOrder = serverEntity.order
			)
		} coAnswers {
			val entityTreeNode = TreeNode(clientEntity)
			rootNode.addChild(entityTreeNode)
			clientEntity
		}
		every { sceneEditorRepository.rawTree } returns tree
		every { sceneEditorRepository.resolveScenePathFromFilesystem(clientEntity.id) } returns filePath
		coEvery { sceneEditorRepository.storeSceneMarkdownRaw(content, filePath) } returns true

		val sync = defaultSceneSynchronizer()
		sync.storeEntity(
			serverEntity = serverEntity,
			syncId = syncId,
			onLog = {}
		)

		coVerify(exactly = 1) {
			sceneEditorService.storeMetadata(
				match { it.tags == setOf("magic", "spoiler") },
				sceneId,
			)
		}
	}

	@Test
	fun `ownsEntity - returns false for unknown scene`() = runTest {
		////////////////////
		// Setup
		val sceneId = 999

		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns null
		every { sceneEditorRepository.getArchivedScenes() } returns emptyList()

		////////////////////
		// Test
		val sync = defaultSceneSynchronizer()
		val owns = sync.ownsEntity(sceneId)

		////////////////////
		// Verify
		kotlin.test.assertFalse(owns, "Should not own unknown scene")
	}
}