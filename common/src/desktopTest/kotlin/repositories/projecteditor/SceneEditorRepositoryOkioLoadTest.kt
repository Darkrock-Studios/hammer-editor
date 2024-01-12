package repositories.projecteditor

import OUT_OF_ORDER_PROJECT_NAME
import PROJECT_1_NAME
import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.ProjectMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.scenemetadatarepository.SceneMetadataDatasource
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
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test
import utils.BaseTest
import utils.callPrivate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneEditorRepositoryOkioLoadTest : BaseTest() {
	private lateinit var ffs: FakeFileSystem
	private lateinit var projectPath: HPath
	private lateinit var projectsRepo: ProjectsRepository
	private lateinit var projectDef: ProjectDef
	private lateinit var repo: SceneEditorRepository
	private lateinit var projectSynchronizer: ClientProjectSynchronizer
	private lateinit var idRepository: IdRepository
	private lateinit var metadataRepository: ProjectMetadataRepository
	private lateinit var metadataDatasource: SceneMetadataDatasource
	private var nextId = -1
	private lateinit var toml: Toml

	private fun claimId(): Int {
		val id = nextId
		nextId++
		return id
	}

	@Before
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		ffs.emulateWindows()

		val rootDir = getDefaultRootDocumentDirectory()
		ffs.createDirectories(rootDir.toPath())

		toml = createTomlSerializer()

		nextId = -1
		idRepository = mockk()
		coEvery { idRepository.claimNextId() } answers { claimId() }

		metadataRepository = mockk()
		metadataDatasource = mockk()

		projectSynchronizer = mockk()
		every { projectSynchronizer.isServerSynchronized() } returns false

		projectsRepo = mockk()
		every { projectsRepo.getProjectsDirectory() } returns
			rootDir.toPath().div(SceneEditorRepositoryOkioMoveTest.PROJ_DIR).toHPath()

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
			projectMetadataRepository = metadataRepository,
			sceneMetadataDatasource = metadataDatasource,
		)
	}

	private fun verifySceneItem(
		id: Int,
		order: Int,
		type: SceneItem.Type,
		node: TreeNode<SceneItem>
	) {
		assertEquals(id, node.value.id, "ID did not match for  Node ${node.value.id}")
		assertEquals(order, node.value.order, "Order did not match for Node ${node.value.id}")
		assertEquals(type, node.value.type, "Type did not match for  Node ${node.value.id}")
	}

	@Test
	fun `Load Project 1`() {
		configure(PROJECT_1_NAME)

		val expectedNodes = mapOf<Int, ((TreeNode<SceneItem>) -> Unit)>(
			0 to { node -> assertTrue(node.value.isRootScene) },
			1 to { node ->
				verifySceneItem(
					id = 1,
					order = 0,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			2 to { node ->
				verifySceneItem(
					id = 2,
					order = 1,
					type = SceneItem.Type.Group,
					node = node
				)
			},
			3 to { node ->
				verifySceneItem(
					id = 3,
					order = 0,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			4 to { node ->
				verifySceneItem(
					id = 4,
					order = 1,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			5 to { node ->
				verifySceneItem(
					id = 5,
					order = 2,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			6 to { node ->
				verifySceneItem(
					id = 6,
					order = 2,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			7 to { node ->
				verifySceneItem(
					id = 7,
					order = 3,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
		)

		val tree = repo.callPrivate<SceneEditorRepository, TreeNode<SceneItem>>("loadSceneTree")

		// +1 to count root
		assertEquals(expectedNodes.size, tree.numChildrenRecursive() + 1)

		tree.forEachIndexed { index, node ->
			expectedNodes[index]!!.invoke(node)
		}
	}

	@Test
	fun `Load Project 2`() {
		configure(PROJECT_2_NAME)

		val expectedNodes = mapOf<Int, ((TreeNode<SceneItem>) -> Unit)>(
			0 to { node -> assertTrue(node.value.isRootScene) },
			1 to { node ->
				verifySceneItem(
					id = 1,
					order = 0,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			2 to { node ->
				verifySceneItem(
					id = 2,
					order = 1,
					type = SceneItem.Type.Group,
					node = node
				)
			},
			3 to { node ->
				verifySceneItem(
					id = 3,
					order = 0,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			4 to { node ->
				verifySceneItem(
					id = 4,
					order = 1,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			5 to { node ->
				verifySceneItem(
					id = 5,
					order = 2,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			6 to { node ->
				verifySceneItem(
					id = 6,
					order = 2,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			7 to { node ->
				verifySceneItem(
					id = 7,
					order = 3,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			8 to { node ->
				verifySceneItem(
					id = 8,
					order = 4,
					type = SceneItem.Type.Group,
					node = node
				)
			},
		)

		val tree = repo.callPrivate<SceneEditorRepository, TreeNode<SceneItem>>("loadSceneTree")

		// +1 to count root
		assertEquals(expectedNodes.size, tree.numChildrenRecursive() + 1)

		tree.forEachIndexed { index, node ->
			expectedNodes[index]!!.invoke(node)
		}
	}

	@Test
	fun `Out Of Order`() {
		configure(OUT_OF_ORDER_PROJECT_NAME)

		val expectedNodes = mapOf<Int, ((TreeNode<SceneItem>) -> Unit)>(
			0 to { node -> assertTrue(node.value.isRootScene) },
			1 to { node ->
				verifySceneItem(
					id = 2,
					order = 1,
					type = SceneItem.Type.Group,
					node = node
				)
			},
			2 to { node ->
				verifySceneItem(
					id = 3,
					order = 0,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			3 to { node ->
				verifySceneItem(
					id = 5,
					order = 2,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			4 to { node ->
				verifySceneItem(
					id = 4,
					order = 10,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			5 to { node ->
				verifySceneItem(
					id = 1,
					order = 1,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			6 to { node ->
				verifySceneItem(
					id = 6,
					order = 2,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
			7 to { node ->
				verifySceneItem(
					id = 7,
					order = 5,
					type = SceneItem.Type.Scene,
					node = node
				)
			},
		)

		val tree = repo.callPrivate<SceneEditorRepository, TreeNode<SceneItem>>("loadSceneTree")

		// +1 to count root
		assertEquals(expectedNodes.size, tree.numChildrenRecursive() + 1)

		tree.forEachIndexed { index, node ->
			expectedNodes[index]!!.invoke(node)
		}
	}
}