package synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneItem.Companion.ROOT_ID
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.SceneEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers.ClientSceneSynchronizer
import com.darkrockstudios.apps.hammer.common.data.rootSceneNode
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
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

class SceneSynchronizerTest : BaseTest() {

	private val def = getProject1Def()

	@MockK
	private lateinit var sceneEditorRepository: SceneEditorRepositoryOkio

	@MockK
	private lateinit var draftRepository: SceneDraftRepositoryOkio

	@MockK
	private lateinit var serverProjectApi: ServerProjectApi

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
		serverProjectApi = serverProjectApi
	)

	@Test
	fun `Download Scene - Unchanged`() = runTest {
		val sceneId = 1
		val syncId = "syncId"
		val serverEntity = ApiProjectEntity.SceneEntity(
			id = 1,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Test Scene",
			path = listOf(0),
			content = "Scene Content"
		)
		val clientEntity = SceneItem.fromApiEntity(serverEntity, def)
		val filePath = HPath("/", "", true)
		val content = SceneContent(clientEntity, serverEntity.content)

		every { sceneEditorRepository.getSceneItemFromId(ROOT_ID) } returns rootSceneNode(def)
		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns clientEntity
		every { sceneEditorRepository.rawTree } returns tree
		every { sceneEditorRepository.getPathFromFilesystem(clientEntity) } returns filePath
		coEvery { sceneEditorRepository.storeSceneMarkdownRaw(content, filePath) } returns true

		rootNode.addChild(TreeNode(clientEntity))

		val sync = defaultSceneSynchronizer()
		sync.storeEntity(
			serverEntity = serverEntity,
			syncId = syncId,
			onLog = {}
		)

		coVerify(exactly = 1) { sceneEditorRepository.storeSceneMarkdownRaw(content, filePath) }
		coVerify(exactly = 1) { sceneEditorRepository.onContentChanged(content, UpdateSource.Sync) }
	}
}