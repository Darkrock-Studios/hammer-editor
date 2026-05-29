package integration

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneSynchronizer
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import utils.TestStrRes
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for archive functionality with sync.
 *
 * These tests use real implementations of SceneRepository and SceneDatasource
 * with FakeFileSystem, testing the actual interaction between components.
 *
 * These tests would have caught the "Tree node not found" bugs that occurred when
 * syncing archived scenes, because they exercise the real code paths rather than
 * mocking everything.
 *
 * Note: Some synchronizer tests that require loadSceneMetadata are excluded because
 * SceneRepository.loadSceneMetadata calls Compose resources (getString) which
 * requires native Skiko libraries not available in unit tests. Those code paths are
 * tested via the repository-level tests which don't hit resource loading.
 */
class ArchiveSyncIntegrationTest : BaseIntegrationTest() {

	private val strRes = TestStrRes()

	private fun createSynchronizer(): ClientSceneSynchronizer {
		val serverProjectApi: ServerProjectApi = mockk()
		val draftRepository: SceneDraftRepository = mockk(relaxed = true)

		return ClientSceneSynchronizer(
			projectDef = projectDef,
			sceneEditorRepository = sceneEditorRepository,
			sceneEditorService = sceneEditorService,
			draftRepository = draftRepository,
			serverProjectApi = serverProjectApi,
			projectMetadataDatasource = projectMetadataDatasource,
			strRes = strRes,
		)
	}

	@Test
	fun `archive scene and get path segments - should return empty list`() = runTest {
		// Setup
		configureProject(PROJECT_2_NAME)
		sceneEditorRepository.initializeSceneEditor()

		// Get a scene to archive (scene ID 1 from test project)
		val sceneToArchive = sceneEditorRepository.getSceneItemFromId(1)
		assertNotNull(sceneToArchive, "Scene 1 should exist")
		assertEquals(SceneItem.Type.Scene, sceneToArchive.type)

		// Archive the scene
		val archived = sceneEditorRepository.archiveScene(sceneToArchive)
		assertTrue(archived, "Scene should be archived successfully")

		// Get the archived scene
		val archivedScenes = sceneEditorRepository.getArchivedScenes()
		val archivedScene = archivedScenes.find { it.id == 1 }
		assertNotNull(archivedScene, "Archived scene should be found")

		// THIS IS THE BUG FIX TEST:
		// getPathSegments should return empty list for archived scenes
		// Previously this would throw "Tree node not found"
		val pathSegments = sceneEditorRepository.getPathSegments(archivedScene)
		assertEquals(emptyList(), pathSegments, "Archived scenes should have empty path segments")
	}

	@Test
	fun `archive scene and load content - should work without tree lookup`() = runTest {
		// Setup
		configureProject(PROJECT_2_NAME)
		sceneEditorRepository.initializeSceneEditor()

		// Get a scene to archive
		val sceneToArchive = sceneEditorRepository.getSceneItemFromId(1)
		assertNotNull(sceneToArchive)

		// Archive the scene
		sceneEditorRepository.archiveScene(sceneToArchive)

		// Get the archived scene
		val archivedScene = sceneEditorRepository.getArchivedScenes().find { it.id == 1 }
		assertNotNull(archivedScene)

		// THIS IS THE BUG FIX TEST:
		// Loading content for archived scene should work via filesystem resolution
		// Previously this would fail because it tried to use getSceneFilePath which uses the tree
		val scenePath = sceneEditorRepository.resolveScenePathFromFilesystemIncludingArchived(archivedScene.id)
		assertNotNull(scenePath, "Should be able to resolve archived scene path from filesystem")

		val content = sceneEditorRepository.loadSceneMarkdownRaw(archivedScene, scenePath)
		assertNotNull(content, "Should be able to load archived scene content")
	}

	@Test
	fun `synchronizer createEntityForId - should work for archived scene`() = runTest {
		// Setup
		configureProject(PROJECT_2_NAME)
		sceneEditorRepository.initializeSceneEditor()

		// Get a scene to archive
		val sceneToArchive = sceneEditorRepository.getSceneItemFromId(1)
		assertNotNull(sceneToArchive)

		// Archive the scene
		sceneEditorRepository.archiveScene(sceneToArchive)

		// Create the synchronizer with real repository
		val synchronizer = createSynchronizer()

		// THIS IS THE MAIN BUG FIX TEST:
		// createEntityForId should work for archived scenes
		// Previously this would throw "Tree node not found" because:
		// 1. loadSceneMarkdownRaw used getSceneFilePath which uses tree lookup
		// 2. getPathSegments used getSceneFilePath which uses tree lookup
		val entity = synchronizer.createEntityForId(1)

		assertNotNull(entity, "Should create entity for archived scene")
		assertEquals(1, entity.id)
		assertTrue(entity.archived, "Entity should be marked as archived")
		assertEquals(emptyList(), entity.path, "Archived entity should have empty path")
	}

	@Test
	fun `synchronizer ownsEntity - should return true for archived scene`() = runTest {
		// Setup
		configureProject(PROJECT_2_NAME)
		sceneEditorRepository.initializeSceneEditor()

		// Get a scene to archive
		val sceneToArchive = sceneEditorRepository.getSceneItemFromId(1)
		assertNotNull(sceneToArchive)

		// Archive the scene
		sceneEditorRepository.archiveScene(sceneToArchive)

		// Verify it's no longer in the tree
		val inTree = sceneEditorRepository.getSceneItemFromId(1)
		assertEquals(null, inTree, "Archived scene should not be in tree")

		// Create the synchronizer
		val synchronizer = createSynchronizer()

		// ownsEntity should still return true for archived scenes
		val owns = synchronizer.ownsEntity(1)
		assertTrue(owns, "Synchronizer should own archived scene")
	}

	@Test
	fun `synchronizer getEntityHash - should work for archived scene`() = runTest {
		// Setup
		configureProject(PROJECT_2_NAME)
		sceneEditorRepository.initializeSceneEditor()

		// Get a scene to archive
		val sceneToArchive = sceneEditorRepository.getSceneItemFromId(1)
		assertNotNull(sceneToArchive)

		// Archive the scene
		sceneEditorRepository.archiveScene(sceneToArchive)

		// Create the synchronizer
		val synchronizer = createSynchronizer()

		// getEntityHash should work for archived scenes
		val hash = synchronizer.getEntityHash(1)
		assertNotNull(hash, "Should be able to get hash for archived scene")
		assertTrue(hash.isNotEmpty(), "Hash should not be empty")
	}

	@Test
	fun `markForSynchronization - should work for archived scene when sync enabled`() = runTest {
		// Setup with sync enabled
		enableServerSync()
		configureProject(PROJECT_2_NAME)
		sceneEditorRepository.initializeSceneEditor()

		// Get a scene to archive
		val sceneToArchive = sceneEditorRepository.getSceneItemFromId(1)
		assertNotNull(sceneToArchive)

		// Archive the scene - this internally calls markForSynchronization
		// Previously this would throw "Tree node not found" when sync was enabled
		// because markForSynchronization called getPathSegments and loadSceneMarkdownRaw
		// which both used tree-based lookups
		val archived = sceneEditorRepository.archiveScene(sceneToArchive)
		assertTrue(archived, "Should be able to archive scene with sync enabled")

		// Verify the scene is in the archive
		val archivedScenes = sceneEditorRepository.getArchivedScenes()
		assertTrue(archivedScenes.any { it.id == 1 }, "Scene should be in archive")
	}

	/**
	 * This test verifies that conflict resolution works for archived scenes.
	 *
	 * The conflict resolution code in ProjectSynchronizationComponent.onSceneConflict
	 * needs to find scenes even if they're archived, and load their content.
	 */
	@Test
	fun `conflict resolution should work for archived scene`() = runTest {
		// Setup
		configureProject(PROJECT_2_NAME)
		sceneEditorRepository.initializeSceneEditor()

		// Get a scene and archive it
		val sceneToArchive = sceneEditorRepository.getSceneItemFromId(1)
		assertNotNull(sceneToArchive)
		sceneEditorRepository.archiveScene(sceneToArchive)

		// Now simulate what happens during conflict resolution:
		// The code tries to get the local scene by ID to build the conflict UI
		val sceneId = 1

		// Use the new method that checks both active tree AND archived scenes
		val local = sceneEditorRepository.getSceneItemFromIdIncludingArchived(sceneId)
		assertNotNull(local, "Should find archived scene for conflict resolution")
		assertTrue(local.archived, "Scene should be marked as archived")

		// Also verify we can load content for the archived scene
		val scenePath = sceneEditorRepository.resolveScenePathFromFilesystemIncludingArchived(sceneId)
		assertNotNull(scenePath, "Should resolve path for archived scene")

		val content = sceneEditorRepository.loadSceneMarkdownRaw(local, scenePath)
		assertNotNull(content, "Should load content for archived scene")

		// And path segments should be empty for archived scenes
		val pathSegments = sceneEditorRepository.getPathSegments(local)
		assertEquals(emptyList(), pathSegments, "Archived scene should have empty path")
	}

	@Test
	fun `hashEntities - should include archived scenes`() = runTest {
		// Setup
		configureProject(PROJECT_2_NAME)
		sceneEditorRepository.initializeSceneEditor()

		// Get initial entity count
		val synchronizer = createSynchronizer()
		val initialHashes = synchronizer.hashEntities(emptyList())
		val initialCount = initialHashes.size

		// Archive a scene
		val sceneToArchive = sceneEditorRepository.getSceneItemFromId(1)
		assertNotNull(sceneToArchive)
		sceneEditorRepository.archiveScene(sceneToArchive)

		// hashEntities should still include the archived scene
		val hashesAfterArchive = synchronizer.hashEntities(emptyList())

		// Count should be the same - we didn't delete the scene, just archived it
		assertEquals(initialCount, hashesAfterArchive.size, "Hash count should be same after archive")

		// The archived scene's hash should be in the set
		val archivedSceneHash = hashesAfterArchive.find { it.id == 1 }
		assertNotNull(archivedSceneHash, "Archived scene should be included in entity hashes")
	}

	@Test
	fun `storeMetadata - should work for archived scene`() = runTest {
		// Setup
		configureProject(PROJECT_2_NAME)
		sceneEditorRepository.initializeSceneEditor()

		// Get a scene and archive it
		val sceneToArchive = sceneEditorRepository.getSceneItemFromId(1)
		assertNotNull(sceneToArchive)
		sceneEditorRepository.archiveScene(sceneToArchive)

		// Verify scene is archived and not in tree
		val inTree = sceneEditorRepository.getSceneItemFromId(1)
		assertEquals(null, inTree, "Archived scene should not be in tree")

		// THIS IS THE BUG FIX TEST:
		// storeMetadata should work for archived scenes
		// Previously this would throw "storeMetadata: Failed to load scene for id"
		val metadata = sceneMetadataRepository.loadSceneMetadata(1)
		val updatedMetadata = metadata.copy(outline = "Updated outline for archived scene")

		// This should not throw
		sceneEditorService.storeMetadata(updatedMetadata, 1)

		// Verify the metadata was stored
		val reloadedMetadata = sceneMetadataRepository.loadSceneMetadata(1)
		assertEquals("Updated outline for archived scene", reloadedMetadata.outline)
	}

	@Test
	fun `createArchivedScene - should create scene directly in archive`() = runTest {
		// Setup
		configureProject(PROJECT_2_NAME)
		sceneEditorRepository.initializeSceneEditor()

		// Get initial counts
		val initialTreeCount = sceneEditorRepository.rawTree.root().toList().size
		val initialArchivedCount = sceneEditorRepository.getArchivedScenes().size

		// Create a scene directly in the archive (simulating sync from server)
		val newId = 999
		val metadata = com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata(
			outline = "Test outline",
			notes = "Test notes"
		)

		val createdScene = sceneEditorRepository.createArchivedScene(
			id = newId,
			name = "Archived From Server",
			order = 0,
			type = SceneItem.Type.Scene,
			content = "Content from server",
			metadata = metadata
		)

		// Verify the scene was created
		assertNotNull(createdScene)
		assertEquals(newId, createdScene.id)
		assertEquals("Archived From Server", createdScene.name)
		assertTrue(createdScene.archived, "Created scene should be marked as archived")

		// Verify it's in the archive, not in the tree
		val inTree = sceneEditorRepository.getSceneItemFromId(newId)
		assertEquals(null, inTree, "Archived scene should not be in active tree")

		val inArchive = sceneEditorRepository.getArchivedSceneFromId(newId)
		assertNotNull(inArchive, "Scene should be in archive")

		// Verify tree count unchanged, archive count increased
		val finalTreeCount = sceneEditorRepository.rawTree.root().toList().size
		val finalArchivedCount = sceneEditorRepository.getArchivedScenes().size
		assertEquals(initialTreeCount, finalTreeCount, "Tree count should be unchanged")
		assertEquals(initialArchivedCount + 1, finalArchivedCount, "Archive count should increase by 1")

		// Verify content can be loaded
		val scenePath = sceneEditorRepository.resolveScenePathFromFilesystemIncludingArchived(newId)
		assertNotNull(scenePath)
		val content = sceneEditorRepository.loadSceneMarkdownRaw(inArchive, scenePath)
		assertEquals("Content from server", content)

		// Verify metadata was stored
		val loadedMetadata = sceneMetadataRepository.loadSceneMetadata(newId)
		assertEquals("Test outline", loadedMetadata.outline)
		assertEquals("Test notes", loadedMetadata.notes)
	}

	@Test
	fun `reIdScene - should work for archived scene`() = runTest {
		// Setup
		configureProject(PROJECT_2_NAME)
		sceneEditorRepository.initializeSceneEditor()

		// Get a scene and archive it
		val sceneToArchive = sceneEditorRepository.getSceneItemFromId(1)
		assertNotNull(sceneToArchive)
		val originalName = sceneToArchive.name
		sceneEditorRepository.archiveScene(sceneToArchive)

		// Verify scene is archived
		val archivedScene = sceneEditorRepository.getArchivedScenes().find { it.id == 1 }
		assertNotNull(archivedScene, "Scene should be in archive")

		// THIS IS THE BUG FIX TEST:
		// reIdScene should work for archived scenes
		// Previously this would throw "Scene 1 does not exist"
		val newId = 999
		sceneEditorRepository.reIdScene(1, newId)

		// Verify the scene was re-id'd in the archive
		val oldIdScene = sceneEditorRepository.getArchivedScenes().find { it.id == 1 }
		assertEquals(null, oldIdScene, "Old ID should no longer exist")

		val newIdScene = sceneEditorRepository.getArchivedScenes().find { it.id == newId }
		assertNotNull(newIdScene, "Scene should exist with new ID")
		assertEquals(originalName, newIdScene.name, "Scene name should be preserved")
		assertTrue(newIdScene.archived, "Scene should still be archived")
	}
}
