package synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.SceneItem.Companion.ROOT_ID
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.findById
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.time.Instant
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneSynchronizerTest : BaseTest() {

	private val def = getProject1Def()

	@MockK
	private lateinit var sceneEditorRepository: SceneRepository

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

		coEvery { sceneEditorService.loadSceneMetadata(any()) } returns SceneMetadata()

		// Backfilling a downloaded scene's null timestamps reads the project's creation time.
		every { projectMetadataDatasource.loadMetadata(any()) } returns
			ProjectMetadata(Info(created = Instant.fromEpochSeconds(1_600_000_000)))
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
			sceneEditorService.createScene(
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
		val stored = sync.storeEntity(
			serverEntity = serverEntity,
			syncId = syncId,
			onLog = {}
		)
		assertTrue(stored)

		////////////////////
		// Verify
		coVerify(exactly = 1) {
			sceneEditorService.createScene(
				parent = rootNode.value,
				sceneName = serverEntity.name,
				forceId = serverEntity.id,
				forceOrder = serverEntity.order
			)
		}
		coVerify(exactly = 1) { sceneEditorRepository.storeSceneMarkdownRaw(content, filePath) }
		coVerify(exactly = 1) { sceneEditorService.onContentChanged(content, UpdateSource.Sync) }
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
		val stored = sync.storeEntity(
			serverEntity = serverEntity,
			syncId = syncId,
			onLog = {}
		)
		assertTrue(stored)

		////////////////////
		// Verify
		coVerify(exactly = 1) { sceneEditorRepository.storeSceneMarkdownRaw(content, filePath) }
		coVerify(exactly = 1) { sceneEditorService.onContentChanged(content, UpdateSource.Sync) }
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
		val stored = sync.storeEntity(
			serverEntity = serverEntity,
			syncId = syncId,
			onLog = {}
		)
		assertTrue(stored)

		////////////////////
		// Verify
		coVerify(exactly = 1) { sceneEditorRepository.storeSceneMarkdownRaw(content, filePath) }
		coVerify(exactly = 1) { sceneEditorService.onContentChanged(content, UpdateSource.Sync) }

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
		val stored = sync.storeEntity(
			serverEntity = serverEntity,
			syncId = syncId,
			onLog = {}
		)
		assertTrue(stored)

		////////////////////
		// Verify
		assertEquals(serverEntity.name, entityTreeNode.value.name)
		coVerify(exactly = 0) { sceneEditorService.createGroup(any(), any(), any(), any()) }
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
			sceneEditorService.createGroup(
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
		val stored = sync.storeEntity(
			serverEntity = serverEntity,
			syncId = syncId,
			onLog = {}
		)
		assertTrue(stored)

		////////////////////
		// Verify
		val newGroupNode = tree.findById(serverEntity.id)
		assertNotNull(newGroupNode)

		coVerify(exactly = 1) { sceneEditorService.createGroup(any(), any(), any(), any()) }
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

		coEvery { sceneEditorService.loadSceneMetadata(sceneId) } returns
			SceneMetadata(confirmedReferences = setOf(1, 2))
		val hashWithRefs = sync.getEntityHash(sceneId)

		coEvery { sceneEditorService.loadSceneMetadata(sceneId) } returns
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

		coEvery { sceneEditorService.loadSceneMetadata(sceneId) } returns
			SceneMetadata(tags = setOf("important", "draft"))
		val hashWithTags = sync.getEntityHash(sceneId)

		coEvery { sceneEditorService.loadSceneMetadata(sceneId) } returns
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
			sceneEditorService.createScene(
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
		val stored = sync.storeEntity(
			serverEntity = serverEntity,
			syncId = syncId,
			onLog = {}
		)
		assertTrue(stored)

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

	@Test
	fun `createEntityForId - builds entity from an active scene`() = runTest {
		val sceneId = 100
		val sceneItem = SceneItem(
			projectDef = def,
			type = SceneItem.Type.Scene,
			id = sceneId,
			name = "Chapter One",
			order = 3,
		)
		val filePath = HPath("/scene.md", "scene.md", false)

		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns sceneItem
		every { sceneEditorRepository.getPathSegments(sceneItem) } returns listOf(0)
		every {
			sceneEditorRepository.resolveScenePathFromFilesystemIncludingArchived(sceneId)
		} returns filePath
		every { sceneEditorRepository.loadSceneMarkdownRaw(sceneItem, filePath) } returns "Body text"
		coEvery { sceneEditorService.loadSceneMetadata(sceneId) } returns
			SceneMetadata(outline = "The outline", notes = "The notes")

		val sync = defaultSceneSynchronizer()
		val entity = sync.createEntityForId(sceneId)

		assertEquals(sceneId, entity.id)
		assertEquals("Chapter One", entity.name)
		assertEquals(3, entity.order)
		assertEquals(ApiSceneType.Scene, entity.sceneType)
		assertEquals("Body text", entity.content)
		assertEquals(listOf(0), entity.path)
		assertEquals("The outline", entity.outline)
		assertEquals("The notes", entity.notes)
		assertFalse(entity.archived)
	}

	@Test
	fun `createEntityForId - a group carries no content`() = runTest {
		val groupId = 200
		val groupItem = SceneItem(
			projectDef = def,
			type = SceneItem.Type.Group,
			id = groupId,
			name = "Act I",
			order = 0,
		)

		every { sceneEditorRepository.getSceneItemFromId(groupId) } returns groupItem
		every { sceneEditorRepository.getPathSegments(groupItem) } returns listOf(0)
		coEvery { sceneEditorService.loadSceneMetadata(groupId) } returns SceneMetadata()

		val sync = defaultSceneSynchronizer()
		val entity = sync.createEntityForId(groupId)

		assertEquals(ApiSceneType.Group, entity.sceneType)
		assertEquals("", entity.content)
	}

	@Test
	fun `storeEntity - returns false when content fails to store`() = runTest {
		val sceneId = 1
		val serverEntity = ApiProjectEntity.SceneEntity(
			id = sceneId,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Test Scene",
			path = listOf(0),
			content = "Scene Content",
			outline = "",
			notes = "",
		)
		val clientEntity = SceneItem.fromApiEntity(serverEntity, def)
		val filePath = HPath("/", "", true)
		val content = SceneContent(clientEntity, serverEntity.content)

		every { sceneEditorRepository.getSceneItemFromId(ROOT_ID) } returns rootSceneNode(def)
		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns clientEntity
		every { sceneEditorRepository.rawTree } returns tree
		every { sceneEditorRepository.resolveScenePathFromFilesystem(clientEntity.id) } returns filePath
		coEvery { sceneEditorRepository.storeSceneMarkdownRaw(content, filePath) } returns false

		rootNode.addChild(TreeNode(clientEntity))

		val sync = defaultSceneSynchronizer()
		val stored = sync.storeEntity(serverEntity, syncId = "syncId", onLog = {})

		assertFalse(stored)
		coVerify(exactly = 0) { sceneEditorService.onContentChanged(any(), any()) }
	}

	@Test
	fun `storeEntity - archived server scene that is absent locally is created in the archive`() =
		runTest {
			val sceneId = 5
			val serverEntity = ApiProjectEntity.SceneEntity(
				id = sceneId,
				sceneType = ApiSceneType.Scene,
				order = 2,
				name = "Old Chapter",
				path = emptyList(),
				content = "Archived content",
				outline = "",
				notes = "",
				archived = true,
			)

			every { sceneEditorRepository.rawTree } returns tree
			every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns null
			every {
				sceneEditorRepository.resolveScenePathFromFilesystemIncludingArchived(sceneId)
			} returns null
			coEvery {
				sceneEditorRepository.createArchivedScene(
					id = sceneId,
					name = any(),
					order = any(),
					type = any(),
					content = any(),
					metadata = any(),
				)
			} returns SceneItem(
				projectDef = def,
				type = SceneItem.Type.Scene,
				id = sceneId,
				name = "Old Chapter",
				order = 2,
				archived = true,
			)

			val sync = defaultSceneSynchronizer()
			val stored = sync.storeEntity(serverEntity, syncId = "syncId", onLog = {})

			assertTrue(stored)
			coVerify(exactly = 1) {
				sceneEditorRepository.createArchivedScene(
					id = sceneId,
					name = "Old Chapter",
					order = 2,
					type = SceneItem.Type.Scene,
					content = "Archived content",
					metadata = any(),
				)
			}
		}

	@Test
	fun `storeEntity - archived server scene archives the active local copy`() = runTest {
		val sceneId = 6
		val serverEntity = ApiProjectEntity.SceneEntity(
			id = sceneId,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Now Archived",
			path = emptyList(),
			content = "content",
			outline = "",
			notes = "",
			archived = true,
		)
		val activeItem = SceneItem(
			projectDef = def,
			type = SceneItem.Type.Scene,
			id = sceneId,
			name = "Now Archived",
			order = 0,
		)
		val archivedItem = activeItem.copy(archived = true)
		val filePath = HPath("/archived.md", "archived.md", false)

		every { sceneEditorRepository.rawTree } returns tree
		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns activeItem
		coEvery { sceneEditorService.archiveScene(activeItem) } returns true
		every {
			sceneEditorRepository.resolveScenePathFromFilesystemIncludingArchived(sceneId)
		} returns filePath
		every { sceneEditorRepository.getArchivedSceneFromId(sceneId) } returns archivedItem
		coEvery { sceneEditorRepository.storeSceneMarkdownRaw(any(), filePath) } returns true

		val sync = defaultSceneSynchronizer()
		val stored = sync.storeEntity(serverEntity, syncId = "syncId", onLog = {})

		assertTrue(stored)
		coVerify(exactly = 1) { sceneEditorService.archiveScene(activeItem) }
	}

	@Test
	fun `deleteEntityLocal - deletes an active scene`() = runTest {
		val sceneId = 1
		val sceneItem = SceneItem(
			projectDef = def,
			type = SceneItem.Type.Scene,
			id = sceneId,
			name = "Doomed",
			order = 0,
		)

		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns sceneItem
		coEvery { sceneEditorService.deleteScene(sceneItem) } returns true

		val sync = defaultSceneSynchronizer()
		sync.deleteEntityLocal(sceneId, onLog = {})

		coVerify(exactly = 1) { sceneEditorService.deleteScene(sceneItem) }
	}

	@Test
	fun `deleteEntityLocal - falls back to deleting an archived scene`() = runTest {
		val sceneId = 2
		val archivedItem = SceneItem(
			projectDef = def,
			type = SceneItem.Type.Scene,
			id = sceneId,
			name = "Archived Doomed",
			order = 0,
			archived = true,
		)

		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns null
		every { sceneEditorRepository.getArchivedSceneFromId(sceneId) } returns archivedItem
		coEvery { sceneEditorService.deleteScene(archivedItem) } returns true

		val sync = defaultSceneSynchronizer()
		sync.deleteEntityLocal(sceneId, onLog = {})

		coVerify(exactly = 1) { sceneEditorService.deleteScene(archivedItem) }
	}

	@Test
	fun `deleteEntityLocal - logs an error when the scene is not found`() = runTest {
		val sceneId = 404
		val logs = mutableListOf<SyncLogMessage>()

		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns null
		every { sceneEditorRepository.getArchivedSceneFromId(sceneId) } returns null

		val sync = defaultSceneSynchronizer()
		sync.deleteEntityLocal(sceneId, onLog = { logs.add(it) })

		coVerify(exactly = 0) { sceneEditorService.deleteScene(any()) }
		assertEquals(SyncLogLevel.ERROR, logs.single().level)
	}

	@Test
	fun `Download Group - moves to a new parent`() = runTest {
		val groupId = 1
		val syncId = "syncId"
		val serverEntity = ApiProjectEntity.SceneEntity(
			id = groupId,
			sceneType = ApiSceneType.Group,
			order = 0,
			name = "Roaming Group",
			path = listOf(0),
			content = "",
			outline = "",
			notes = "",
		)
		val clientGroupEntity = SceneItem(
			projectDef = def,
			type = SceneItem.Type.Group,
			id = groupId,
			name = "Roaming Group",
			order = 0,
		)

		every { sceneEditorRepository.getSceneItemFromId(ROOT_ID) } returns rootSceneNode(def)
		every { sceneEditorRepository.getSceneItemFromId(groupId) } returns clientGroupEntity
		every { sceneEditorRepository.rawTree } returns tree

		val parentGroupEntity = SceneItem(
			projectDef = def,
			type = SceneItem.Type.Group,
			id = 2,
			name = "Old Parent",
			order = 0,
		)
		val parentGroupNode = TreeNode(parentGroupEntity)
		rootNode.addChild(parentGroupNode)
		val groupNode = TreeNode(clientGroupEntity)
		parentGroupNode.addChild(groupNode)

		val sync = defaultSceneSynchronizer()
		assertTrue(sync.storeEntity(serverEntity, syncId = syncId, onLog = {}))

		assertEquals(0, groupNode.parent?.value?.id)
	}

	@Test
	fun `storeEntity - unarchives a locally-archived scene the server now reports active`() = runTest {
		val sceneId = 7
		val syncId = "syncId"
		val serverEntity = ApiProjectEntity.SceneEntity(
			id = sceneId,
			sceneType = ApiSceneType.Scene,
			order = 0,
			name = "Back In Play",
			path = listOf(0),
			content = "Scene Content",
			outline = "",
			notes = "",
		)
		val activeItem = SceneItem.fromApiEntity(serverEntity, def)
		val archivedItem = activeItem.copy(archived = true)
		val filePath = HPath("/", "", true)
		val content = SceneContent(activeItem, serverEntity.content)

		every { sceneEditorRepository.getSceneItemFromId(ROOT_ID) } returns rootSceneNode(def)
		every { sceneEditorRepository.getSceneItemFromId(sceneId) } returns activeItem
		every { sceneEditorRepository.getArchivedSceneFromId(sceneId) } returns archivedItem
		coEvery { sceneEditorService.unarchiveScene(archivedItem) } returns activeItem
		every { sceneEditorRepository.rawTree } returns tree
		every { sceneEditorRepository.resolveScenePathFromFilesystem(sceneId) } returns filePath
		coEvery { sceneEditorRepository.storeSceneMarkdownRaw(content, filePath) } returns true

		rootNode.addChild(TreeNode(activeItem))

		val sync = defaultSceneSynchronizer()
		val stored = sync.storeEntity(serverEntity, syncId = syncId, onLog = {})

		assertTrue(stored)
		coVerify(exactly = 1) { sceneEditorService.unarchiveScene(archivedItem) }
	}

	@Test
	fun `reIdEntity - re-ids the scene and its drafts`() = runTest {
		val sync = defaultSceneSynchronizer()
		sync.reIdEntity(oldId = 4, newId = 9)

		coVerify(exactly = 1) { sceneEditorRepository.reIdScene(4, 9) }
		coVerify(exactly = 1) { draftRepository.reIdScene(oldId = 4, newId = 9) }
	}
}