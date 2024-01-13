package synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneItem.Companion.ROOT_ID
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers.ClientSceneSynchronizer
import com.darkrockstudios.apps.hammer.common.data.rootSceneNode
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.findById
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import dev.icerock.moko.resources.StringResource
import getProject1Def
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import utils.BaseTest
import utils.fromApiEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SceneSynchronizerTest : BaseTest() {

	private val def = getProject1Def()

	@MockK
	private lateinit var sceneEditorRepository: SceneEditorRepositoryOkio

	@MockK
	private lateinit var draftRepository: SceneDraftRepositoryOkio

	@MockK
	private lateinit var serverProjectApi: ServerProjectApi

	private val strRes: StrRes = object : StrRes {
		override fun get(str: StringResource) = "test"
		override fun get(str: StringResource, vararg args: Any) = "test"
	}

	private lateinit var rootNode: TreeNode<SceneItem>
	private lateinit var tree: Tree<SceneItem>

	@Before
	fun begin() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)

		tree = Tree()
		rootNode = TreeNode(rootSceneNode(def))
		tree.setRoot(rootNode)
	}

	private fun defaultSceneSynchronizer() = ClientSceneSynchronizer(
		projectDef = def,
		sceneEditorRepository = sceneEditorRepository,
		draftRepository = draftRepository,
		serverProjectApi = serverProjectApi,
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
		every { sceneEditorRepository.getPathFromFilesystem(clientEntity) } returns filePath
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
		every { sceneEditorRepository.getPathFromFilesystem(clientEntity) } returns filePath
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
		every { sceneEditorRepository.getPathFromFilesystem(clientSceneEntity) } returns filePath
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
}